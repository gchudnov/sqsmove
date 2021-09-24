package com.github.gchudnov.sqsmove.sqs

import com.github.gchudnov.sqsmove.metrics.getMetricCount
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{ GetQueueUrlRequest, Message, ReceiveMessageRequest }
import zio._
import zio.logging.{ LogLevel, Logger }
import zio.zmx.metrics.MetricAspect
import zio.{ Fiber, Ref, Schedule, ZIO, ZRef }
import zio.Console._

import java.io.IOException
import scala.collection.immutable.IndexedSeq
import scala.jdk.CollectionConverters._

/**
 * Basic SQS Functionality
 */
abstract class BasicSqsMove(maxConcurrency: Int, logger: Logger[String]) extends Sqs.Service {
  import AwsSqs._

  protected val sqsClient: SqsAsyncClient = makeSqsClient(makeHttpClient(maxConcurrency))

  override def getQueueUrl(name: String): ZIO[Any, Throwable, String] =
    ZIO
      .fromFutureJava(sqsClient.getQueueUrl(GetQueueUrlRequest.builder.queueName(name).build()))
      .map(_.queueUrl())

  /**
   * Receives a batch of messages. When there are no messages, the function return an empty batch (size = 0)
   */
  protected def receiveBatch(r: ReceiveMessageRequest): ZIO[Any, Throwable, IndexedSeq[Message]] =
    ZIO
      .fromFutureJava(sqsClient.receiveMessage(r))
      .map(resp => resp.messages().asScala.toIndexedSeq)

  protected def sendBatch(queueUrl: String, b: IndexedSeq[Message]): ZIO[Any, Throwable, IndexedSeq[ReceiptHandle]] = {
    val bi      = b.zipWithIndex
    val m       = bi.map(it => (it._2.toString, it._1.receiptHandle())).toMap
    val reqSend = toBatchRequest(queueUrl, bi.map((toBatchRequestEntry _).tupled))
    ZIO
      .fromFutureJava(sqsClient.sendMessageBatch(reqSend))
      .flatMap { resp =>
        val fs = resp.failed().asScala
        val ss = resp.successful().asScala

        val ids = ss.flatMap(e => m.get(e.id())).toIndexedSeq

        ZIO
          .when(fs.nonEmpty)(logger.log(LogLevel.Warn)(s"Failed to send ${fs.size} entries"))
          .as(ids)
      }
  }

  protected def deleteBatch(queueUrl: String, b: IndexedSeq[ReceiptHandle]): ZIO[Any, Throwable, Int] = {
    val reqDel = toDeleteRequest(queueUrl, b.zipWithIndex.map((toDeleteRequestEntry _).tupled))
    ZIO
      .fromFutureJava(sqsClient.deleteMessageBatch(reqDel))
      .flatMap { resp =>
        val fs = resp.failed().asScala
        val ss = resp.successful().asScala

        ZIO
          .when(fs.nonEmpty)(logger.log(LogLevel.Warn)(s"Failed to delete ${fs.size} entries"))
          .as(ss.length)
      }
  }
}

object BasicSqsMove {
  val metricCounterName: String           = "countMessages"
  val aspCountMessages: MetricAspect[Int] = MetricAspect.countValueWith[Int](metricCounterName)(_.toDouble)

  private val monitorDuration = 1.second

  def monitor(): ZIO[Has[Console] with Has[Clock], Nothing, Fiber.Runtime[IOException, Long]] = {
    val schedulePolicy =
      Schedule.spaced(monitorDuration)

    val iteration = (mRef: Ref[Double]) =>
      for {
        pCount <- mRef.get
        cCount <- mRef.updateAndGet(_ => getMetricCount(BasicSqsMove.metricCounterName).getOrElse(0.0))
        dMsg    = cCount - pCount
        _      <- printLine(s"SQS messages moved: ${cCount.toInt} (+${dMsg.toInt})").when(dMsg > 0)
      } yield ()

    for {
      mRef <- ZRef.make(0.0)
      f    <- iteration(mRef).repeat(schedulePolicy).fork
    } yield f
  }
}

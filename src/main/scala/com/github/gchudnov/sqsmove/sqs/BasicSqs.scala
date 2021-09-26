package com.github.gchudnov.sqsmove.sqs

import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{ GetQueueUrlRequest, Message, MessageAttributeValue, ReceiveMessageRequest }
import zio.*
import zio.Console.*

import java.io.{ File, IOException }
import scala.collection.immutable.IndexedSeq
import scala.jdk.CollectionConverters.*
import com.github.gchudnov.sqsmove.files.FileOps
import com.github.gchudnov.sqsmove.csvs.CsvOps
import java.nio.file.Paths

/**
 * Basic SQS Functionality
 */
abstract class BasicSqs(maxConcurrency: Int) extends Sqs:
  import AwsSqs.*

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

  protected def sendBatch(queueUrl: String, b: IndexedSeq[Message]): ZIO[Any, Throwable, IndexedSeq[ReceiptHandle]] =
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
          .when(fs.nonEmpty)(ZIO.logWarning(s"Failed to send ${fs.size} entries"))
          .as(ids)
      }

  protected def deleteBatch(queueUrl: String, b: IndexedSeq[ReceiptHandle]): ZIO[Any, Throwable, Int] =
    val reqDel = toDeleteRequest(queueUrl, b.zipWithIndex.map((toDeleteRequestEntry _).tupled))
    ZIO
      .fromFutureJava(sqsClient.deleteMessageBatch(reqDel))
      .flatMap { resp =>
        val fs = resp.failed().asScala
        val ss = resp.successful().asScala

        ZIO
          .when(fs.nonEmpty)(ZIO.logWarning(s"Failed to delete ${fs.size} entries"))
          .as(ss.length)
      }

  protected def saveBatch(dstDir: File, b: IndexedSeq[Message]): ZIO[Any, Throwable, IndexedSeq[ReceiptHandle]] =
    ZIO.foreach(b)(m =>
      for
        filePath <- ZIO.attempt(Paths.get(dstDir.getAbsolutePath, m.messageId))
        _        <- ZIO.fromEither(FileOps.saveString(filePath.toFile, m.body()))
        _        <- ZIO.fromEither(FileOps.saveString(FileOps.replaceExtension(filePath.toFile, "meta"), CsvOps.asString(toTable(m.messageAttributes.asScala.toMap))))
      yield m.receiptHandle
    )

  private def toTable(m: Map[String, MessageAttributeValue]): List[List[String]] =
    val header = List("name", "type", "value")
    val lines  = m.map((k, ma) => List(k, ma.dataType, ma.stringValue)).toList
    header :: lines

object BasicSqs:
  val metricCounterName: String             = "countMessages"
  val countMessages: ZIOMetric.Counter[Int] = ZIOMetric.countValueWith[Int](metricCounterName)(_.toDouble)

  private val monitorDuration = 1.second

  def monitor(): ZIO[Has[Console] with Has[Clock], Nothing, Fiber.Runtime[IOException, Long]] =
    val schedulePolicy = Schedule.spaced(monitorDuration)

    val iteration = (mRef: Ref[Double]) =>
      for
        cCount <- countMessages.count
        pCount <- mRef.getAndSet(cCount)
        dMsg    = cCount - pCount
        _      <- Clock.currentDateTime.flatMap(dt => printLine(s"[$dt] SQS messages moved: ${cCount.toInt} (+${dMsg.toInt})").when(dMsg > 0))
      yield ()

    for
      mRef <- ZRef.make(0.0)
      f    <- iteration(mRef).repeat(schedulePolicy).fork
    yield f

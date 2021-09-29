package com.github.gchudnov.sqsmove.sqs

import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{ GetQueueUrlRequest, Message, MessageAttributeValue, ReceiveMessageRequest }
import software.amazon.awssdk.core.SdkBytes
import zio.*
import zio.Console.*

import java.io.{ File, IOException }
import scala.collection.immutable.IndexedSeq
import scala.jdk.CollectionConverters.*
import com.github.gchudnov.sqsmove.util.FileOps
import com.github.gchudnov.sqsmove.util.CsvOps
import com.github.gchudnov.sqsmove.util.ArrayOps
import java.nio.file.Paths
import scala.util.control.Exception.*

/**
 * Basic SQS Functionality
 */
abstract class BasicSqs(maxConcurrency: Int) extends Sqs:
  import AwsSqs.*
  import BasicSqs.*

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
        _        <- ZIO.fromEither(FileOps.saveString(filePath.toFile, m.body))
        attrMap   = m.messageAttributes.asScala.toMap
        meta      = CsvOps.tableToString(attrsToTable(attrMap))
        _        <- ZIO.fromEither(FileOps.saveString(FileOps.replaceExtension(filePath.toFile, BasicSqs.extMeta), meta)).when(attrMap.nonEmpty)
      yield m.receiptHandle
    )

object BasicSqs:
  val attrName  = "name"
  val attrType  = "type"
  val attrValue = "value"

  val extMeta = "meta"

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
        _      <- Clock.currentDateTime.flatMap(dt => printLine(s"[$dt] SQS messages processed: ${cCount.toInt} (+${dMsg.toInt})").when(dMsg > 0))
      yield ()

    for
      mRef <- ZRef.make(0.0)
      f    <- iteration(mRef).repeat(schedulePolicy).fork
    yield f

  private[sqs] def attrsToTable(m: Map[String, MessageAttributeValue]): List[List[String]] =
    import BasicSqs.*
    val header = List(attrName, attrType, attrValue)
    val lines = m
      .map((k, ma) =>
        val value = ma.dataType match
          case "String" => ma.stringValue
          case "Number" => ma.stringValue
          case "Binary" => ArrayOps.bytesToBase64(ma.binaryValue.asByteArray)
          case _        => sys.error(s"Unsupported SQS Message DataType: '${ma.dataType}' when decoding metadata")
        List(k, ma.dataType, value)
      )
      .toList
    header :: lines

  private[sqs] def attrsFromTable(t: List[List[String]]): Either[Throwable, Map[String, MessageAttributeValue]] =
    if t.isEmpty then Right[Throwable, Map[String, MessageAttributeValue]](Map.empty[String, MessageAttributeValue])
    else
      val header :: tail = t
      for
        attrNameIdx  <- findColumnIndex(header, attrName)
        attrTypeIdx  <- findColumnIndex(header, attrType)
        attrValueIdx <- findColumnIndex(header, attrValue)
        m <- allCatch.either(
               tail
                 .map((row) =>
                   val colName  = row(attrNameIdx)
                   val colType  = row(attrTypeIdx)
                   val colValue = row(attrValueIdx)

                   val msgAttr = (colType match
                     case "String" => MessageAttributeValue.builder().stringValue(colValue).dataType("String").build()
                     case "Number" => MessageAttributeValue.builder().stringValue(colValue).dataType("Number").build()
                     case "Binary" => MessageAttributeValue.builder().binaryValue(SdkBytes.fromUtf8String(colValue)).dataType("Binary").build()
                     case _        => throw IllegalArgumentException(s"Unsupported SQS Message DataType: '${colType}' when encoding metadata")
                   )

                   (colName, msgAttr)
                 )
                 .toMap
             )
      yield m

  private def findColumnIndex(header: List[String], name: String): Either[Throwable, Int] =
    val n = header.indexOf(name, 0)
    if n != -1 then Right(n)
    else Left(new IllegalArgumentException(s"${name} not found in metadata table"))

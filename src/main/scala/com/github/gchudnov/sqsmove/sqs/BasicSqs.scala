package com.github.gchudnov.sqsmove.sqs

import com.github.gchudnov.sqsmove.util.{ ArrayOps, CsvOps, DirOps, FileOps }
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.*
import zio.*
import zio.Console.*
import zio.metrics.*

import java.io.{ File, IOException }
import java.nio.file.Paths
import scala.collection.immutable.IndexedSeq
import scala.jdk.CollectionConverters.*
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
          .when(fs.nonEmpty)(ZIO.logWarning(s"Failed to send ${fs.size} entries: ${errorsToString(fs)}"))
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
          .when(fs.nonEmpty)(ZIO.logWarning(s"Failed to delete ${fs.size} entries: ${errorsToString(fs)}"))
          .as(ss.length)
      }

  protected def saveBatch(dstDir: File, b: IndexedSeq[Message]): ZIO[Any, Throwable, IndexedSeq[ReceiptHandle]] =
    ZIO.foreach(b)(m =>
      for
        filePath <- ZIO.attempt(Paths.get(dstDir.getAbsolutePath, m.messageId))
        _        <- ZIO.fromEither(FileOps.saveString(filePath.toFile, m.body))
        attrMap   = m.messageAttributes.asScala.toMap
        meta     <- ZIO.fromEither(CsvOps.tableToString(attrsToTable(attrMap)))
        _        <- ZIO.fromEither(FileOps.saveString(FileOps.replaceExtension(filePath.toFile, BasicSqs.extMeta), meta)).when(attrMap.nonEmpty)
      yield m.receiptHandle
    )

object BasicSqs:
  val attrName  = "name"
  val attrType  = "type"
  val attrValue = "value"

  val extMeta = "meta"

  val metricCounterName: String          = "countMessages"
  val countMessages: Metric.Counter[Int] = Metric.counterInt(metricCounterName)

  private val monitorDuration = 1.second

  def summary(): ZIO[Any, IOException, Unit] =
    for
      cCount <- countMessages.value.map(_.count)
      _      <- Clock.currentDateTime.flatMap(dt => printLine(s"[$dt] SQS messages processed: ${cCount.toInt}"))
    yield ()

  def monitor(): ZIO[Any, Nothing, Fiber.Runtime[IOException, Long]] =
    val schedulePolicy = Schedule.spaced(monitorDuration)

    val iteration = (mRef: Ref[Double]) =>
      for
        cCount <- countMessages.value.map(_.count)
        pCount <- mRef.getAndSet(cCount)
        dMsg    = cCount - pCount
        _      <- Clock.currentDateTime.flatMap(dt => printLine(s"[$dt] SQS messages processed: ${cCount.toInt} (+${dMsg.toInt})").when(dMsg > 0))
      yield ()

    for
      mRef <- Ref.make(0.0)
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
      val (header, tail) = (t.head, t.tail)
      for
        attrNameIdx  <- findColumnIndex(header, attrName).toRight(columnNotFound(attrName))
        attrTypeIdx  <- findColumnIndex(header, attrType).toRight(columnNotFound(attrName))
        attrValueIdx <- findColumnIndex(header, attrValue).toRight(columnNotFound(attrName))
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

  private def columnNotFound(name: String): Throwable =
    new IllegalArgumentException(s"Column '${name}' is not found in metadata")

  private[sqs] def findColumnIndex(header: Seq[String], name: String): Option[Int] =
    header.indexOf(name, 0) match
      case n if n != -1 => Some(n)
      case _            => None

  /**
   * List all files in the directory, excluding files ending with `.meta` suffix.
   */
  private[sqs] def listFilesWithoutMetadata(dir: File): Either[Throwable, List[File]] =
    DirOps.listFilesBy(dir, file => !file.getName.endsWith(extMeta))

  /**
   * Create am SQS Message (possibly with metadata)
   */
  private[sqs] def messageFromFile(file: File): ZIO[Any, Throwable, Message] =
    for
      dm          <- readDataWithMetadata(file)
      (data, meta) = dm
      m           <- ZIO.fromEither(BasicSqs.toMessage(data, meta))
    yield m

  private[sqs] def isFileNonEmpty(file: File): ZIO[Any, Throwable, Boolean] =
    for
      flag <- ZIO.fromEither(FileOps.isFileEmpty(file))
      _    <- ZIO.when(flag)(ZIO.logWarning(s"File ${file} is empty: skipping"))
    yield !flag

  /**
   * Read both data and metadata for the given file
   */
  private[sqs] def readDataWithMetadata(file: File): ZIO[Any, Throwable, (String, Option[String])] =
    for
      data     <- ZIO.fromEither(FileOps.readAll(file))
      metaFile  = FileOps.replaceExtension(file, extMeta)
      metaData <- ZIO.fromEither(FileOps.readAll(metaFile)).when(metaFile.exists())
    yield (data, metaData)

  /**
   * Makes a message from the raw data and metadata
   */
  private[sqs] def toMessage(data: String, metadata: Option[String]): Either[Throwable, Message] =
    for
      metaTable <- CsvOps.tableFromString(metadata.getOrElse(""))
      attrs     <- attrsFromTable(metaTable)
      m         <- allCatch.either(Message.builder.messageAttributes(attrs.asJava).body(data).build())
    yield m

  private[sqs] def errorsToString(fs: Iterable[BatchResultErrorEntry]): String =
    fs.map(_.toString).mkString(";")

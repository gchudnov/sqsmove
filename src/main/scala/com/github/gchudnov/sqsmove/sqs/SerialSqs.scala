package com.github.gchudnov.sqsmove.sqs

import com.github.gchudnov.sqsmove.sqs.AwsSqs.makeReceiveRequest
import com.github.gchudnov.sqsmove.sqs.BasicSqs.{ countMessages, messageFromFile }
import com.github.gchudnov.sqsmove.util.DirOps
import zio.*

import java.io.File

/**
 * Serial SQS Move
 */
final class SerialSqs(maxConcurrency: Int, limit: Option[Int], visibilityTimeout: Duration, isDelete: Boolean) extends BasicSqs(maxConcurrency):
  import BasicSqs.*

  override def move(srcQueueUrl: String, dstQueueUrl: String): ZIO[Any, Throwable, Unit] =
    for
      nRef <- Ref.make(limit.getOrElse(Int.MaxValue))
      _ <- (for
             n <- nRef.get
             sz = math.min(AwsSqs.maxBatchSize, n)
             r <- ZIO.attempt(makeReceiveRequest(srcQueueUrl, visibilityTimeoutSec = visibilityTimeout.getSeconds, batchSize = sz))
             b <- receiveBatch(r)
             _ <- sendBatch(dstQueueUrl, b).flatMap(b => deleteBatch(srcQueueUrl, b).when(isDelete).as(b.size) @@ countMessages).when(b.nonEmpty)
             k <- nRef.updateAndGet(_ - b.size)
           yield k).repeatWhile(_ > 0)
    yield ()

  override def download(srcQueueUrl: String, dstDir: File): ZIO[Any, Throwable, Unit] =
    for
      nRef <- Ref.make(limit.getOrElse(Int.MaxValue))
      _ <- (for
             n <- nRef.get
             sz = math.min(AwsSqs.maxBatchSize, n)
             r <- ZIO.attempt(makeReceiveRequest(srcQueueUrl, visibilityTimeoutSec = visibilityTimeout.getSeconds, batchSize = sz))
             b <- receiveBatch(r)
             _ <- saveBatch(dstDir, b).flatMap(b => deleteBatch(srcQueueUrl, b).when(isDelete).as(b.size) @@ countMessages).when(b.nonEmpty)
             k <- nRef.updateAndGet(_ - b.size)
           yield k).repeatWhile(_ > 0)
    yield ()

  override def upload(srcDir: File, dstQueueUrl: String): ZIO[Any, Throwable, Unit] =
    for
      files       <- ZIO.fromEither(BasicSqs.listFilesWithoutMetadata(srcDir))
      limitedFiles = files.take(limit.fold(Int.MaxValue)(identity))
      filteredFiles <- ZIO.collect(limitedFiles)(f =>
                         isFileNonEmpty(f).asSomeError.flatMap {
                           case true  => ZIO.succeed(f)
                           case false => ZIO.fail(None)
                         }
                       )
      chunkedFiles = filteredFiles.grouped(AwsSqs.maxBatchSize).toList
      _ <- ZIO.foreachDiscard(chunkedFiles) { chunk =>
             ZIO
               .foreach(chunk)(messageFromFile)
               .flatMap(b => sendBatch(dstQueueUrl, b.toIndexedSeq).as(b.size) @@ countMessages)
           }
    yield ()

object SerialSqs:
  def layer(maxConcurrency: Int, limit: Option[Int], visibilityTimeout: Duration, isDelete: Boolean): ZLayer[Any, Nothing, Sqs] =
    ZLayer.succeed(
      new SerialSqs(
        maxConcurrency = maxConcurrency,
        limit = limit,
        visibilityTimeout = visibilityTimeout,
        isDelete = isDelete
      )
    )

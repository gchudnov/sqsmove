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

  override def move(srcQueueUrl: String, dstQueueUrl: String): ZIO[Any, Throwable, Unit] =
    for
      nRef <- ZRef.make(limit.getOrElse(Int.MaxValue))
      _ <- (for
             n <- nRef.get
             sz = math.min(AwsSqs.maxBatchSize, n)
             r <- ZIO.attempt(makeReceiveRequest(srcQueueUrl, visibilityTimeoutSec = visibilityTimeout.getSeconds, batchSize = sz))
             b <- receiveBatch(r)
             _ <- sendBatch(dstQueueUrl, b).flatMap(b => deleteBatch(srcQueueUrl, b).when(isDelete).as(b.size) @@ countMessages).when(b.nonEmpty)
             k <- nRef.updateAndGet(_ - b.size)
           yield k).repeatUntil(_ > 0)
    yield ()

  override def download(srcQueueUrl: String, dstDir: File): ZIO[Any, Throwable, Unit] =
    for
      nRef <- ZRef.make(limit.getOrElse(Int.MaxValue))
      _ <- (for
             n <- nRef.get
             sz = math.min(AwsSqs.maxBatchSize, n)
             r <- ZIO.attempt(makeReceiveRequest(srcQueueUrl, visibilityTimeoutSec = visibilityTimeout.getSeconds, batchSize = sz))
             b <- receiveBatch(r)
             _ <- saveBatch(dstDir, b).flatMap(b => deleteBatch(srcQueueUrl, b).when(isDelete).as(b.size) @@ countMessages).when(b.nonEmpty)
             k <- nRef.updateAndGet(_ - b.size)
           yield k).repeatUntil(_ > 0)
    yield ()

  override def upload(srcDir: File, dstQueueUrl: String): ZIO[Any, Throwable, Unit] =
    ZIO
      .fromEither(BasicSqs.listFilesWithoutMetadata(srcDir))
      .map(_.grouped(AwsSqs.maxBatchSize).take(limit.fold(Int.MaxValue)(identity)).toList)
      .flatMap(chunkedFiles =>
        ZIO.foreach(chunkedFiles)(chunk =>
          ZIO
            .foreach(chunk)(messageFromFile)
            .flatMap(b => sendBatch(dstQueueUrl, b.toIndexedSeq).as(b.size) @@ countMessages)
        )
      )
      .unit

object SerialSqs:
  def layer(maxConcurrency: Int, limit: Option[Int], visibilityTimeout: Duration, isDelete: Boolean): ZLayer[Any, Throwable, Has[Sqs]] =
    ZIO.attempt(new SerialSqs(maxConcurrency = maxConcurrency, limit = limit, visibilityTimeout = visibilityTimeout, isDelete = isDelete)).toLayer

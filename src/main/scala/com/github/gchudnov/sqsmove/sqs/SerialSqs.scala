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
    ZIO
      .succeed(makeReceiveRequest(srcQueueUrl, visibilityTimeoutSec = visibilityTimeout.getSeconds, limit = limit))
      .flatMap(r => receiveBatch(r))
      .flatMap(b => sendBatch(dstQueueUrl, b).flatMap(b => deleteBatch(srcQueueUrl, b).when(isDelete).as(b.size) @@ countMessages).when(b.nonEmpty))
      .forever

  override def download(srcQueueUrl: String, dstDir: File): ZIO[Any, Throwable, Unit] =
    ZIO
      .succeed(makeReceiveRequest(srcQueueUrl, visibilityTimeoutSec = visibilityTimeout.getSeconds, limit = limit))
      .flatMap(r => receiveBatch(r))
      .flatMap(b => saveBatch(dstDir, b).flatMap(b => deleteBatch(srcQueueUrl, b).when(isDelete).as(b.size) @@ countMessages).when(b.nonEmpty))
      .forever

  override def upload(srcDir: File, dstQueueUrl: String): ZIO[Any, Throwable, Unit] =
    ZIO
      .fromEither(BasicSqs.listFilesWithoutMetadata(srcDir))
      .map(_.grouped(AwsSqs.maxBatchSize).toList)
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

package com.github.gchudnov.sqsmove.sqs

import com.github.gchudnov.sqsmove.sqs.AwsSqs.makeReceiveRequest
import zio.*
import zio.stream.ZStream
import java.io.File

/**
 * Parallel SQS Move
 */
final class ParallelSqs(maxConcurrency: Int, parallelism: Int, visibilityTimeout: Duration, isDelete: Boolean) extends BasicSqs(maxConcurrency):
  import BasicSqs.*

  override def move(srcQueueUrl: String, dstQueueUrl: String): ZIO[Any, Throwable, Unit] =
    ZStream
      .repeat(makeReceiveRequest(srcQueueUrl, visibilityTimeoutSec = visibilityTimeout.getSeconds))
      .mapZIOPar(parallelism)(r => receiveBatch(r))
      .filter(_.nonEmpty)
      .mapZIOPar(parallelism)(b => sendBatch(dstQueueUrl, b))
      .filter(_.nonEmpty)
      .mapZIOPar(parallelism)(b => (deleteBatch(srcQueueUrl, b).when(isDelete).as(b.size) @@ countMessages).unit)
      .runDrain

  override def download(srcQueueUrl: String, dstDir: File): ZIO[Any, Throwable, Unit] =
    ZStream
      .repeat(makeReceiveRequest(srcQueueUrl, visibilityTimeoutSec = visibilityTimeout.getSeconds))
      .mapZIOPar(parallelism)(r => receiveBatch(r))
      .filter(_.nonEmpty)
      .mapZIOPar(parallelism)(b => saveBatch(dstDir, b))
      .filter(_.nonEmpty)
      .mapZIOPar(parallelism)(b => (deleteBatch(srcQueueUrl, b).when(isDelete).as(b.size) @@ countMessages).unit)
      .runDrain

  override def upload(srcDir: File, dstQueueUrl: String): ZIO[Any, Throwable, Unit] =
    ZStream
      .fromIterableZIO(ZIO.fromEither(BasicSqs.listFilesWithoutMetadata(srcDir)))
      .grouped(AwsSqs.receiveMaxNumberOfMessages)
      .filter(_.nonEmpty)
      .mapZIOPar(parallelism)(b => ZIO.foreach(b)(messageFromFile).flatMap(b => sendBatch(dstQueueUrl, b).as(b.size) @@ countMessages))
      .runDrain

object ParallelSqs:
  def layer(maxConcurrency: Int, parallelism: Int, visibilityTimeout: Duration, isDelete: Boolean): ZLayer[Any, Throwable, Has[Sqs]] =
    ZIO.attempt(new ParallelSqs(maxConcurrency = maxConcurrency, parallelism = parallelism, visibilityTimeout = visibilityTimeout, isDelete = isDelete)).toLayer

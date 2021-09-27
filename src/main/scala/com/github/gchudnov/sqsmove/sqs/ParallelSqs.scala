package com.github.gchudnov.sqsmove.sqs

import com.github.gchudnov.sqsmove.sqs.AwsSqs.makeReceiveRequest
import zio.*
import zio.stream.ZStream
import java.io.File

/**
 * Parallel SQS Move
 */
final class ParallelSqs(maxConcurrency: Int, parallelism: Int, visibilityTimeout: Duration, isNoDelete: Boolean) extends BasicSqs(maxConcurrency):
  import BasicSqs.*

  override def move(srcQueueUrl: String, dstQueueUrl: String): ZIO[Any, Throwable, Unit] =
    ZStream
      .repeat(makeReceiveRequest(srcQueueUrl, visibilityTimeoutSec = visibilityTimeout.getSeconds))
      .mapZIOPar(parallelism)(r => receiveBatch(r))
      .filter(_.nonEmpty)
      .mapZIOPar(parallelism)(b => sendBatch(dstQueueUrl, b))
      .filter(_.nonEmpty)
      .mapZIOPar(parallelism)(b => (deleteBatch(srcQueueUrl, b) @@ countMessages).unit)
      .runDrain

  override def download(srcQueueUrl: String, dstDir: File): ZIO[Any, Throwable, Unit] = ???

  override def upload(stcDir: File, dstQueueUrl: String): ZIO[Any, Throwable, Unit] = ???

object ParallelSqs:
  def layer(maxConcurrency: Int, parallelism: Int, visibilityTimeout: Duration, isNoDelete: Boolean): ZLayer[Any, Throwable, Has[Sqs]] =
    ZIO.attempt(new ParallelSqs(maxConcurrency = maxConcurrency, parallelism = parallelism, visibilityTimeout = visibilityTimeout, isNoDelete = isNoDelete)).toLayer

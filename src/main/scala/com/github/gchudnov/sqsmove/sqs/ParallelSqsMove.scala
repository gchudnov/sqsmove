package com.github.gchudnov.sqsmove.sqs

import com.github.gchudnov.sqsmove.sqs.AwsSqs.makeReceiveRequest
import zio.*
import zio.stream.ZStream
import java.io.File

/**
 * Parallel SQS Move
 */
final class ParallelSqsMove(maxConcurrency: Int, parallelism: Int) extends BasicSqsMove(maxConcurrency):
  import BasicSqsMove.*

  override def move(srcQueueUrl: String, dstQueueUrl: String): ZIO[Any, Throwable, Unit] =
    ZStream
      .repeat(makeReceiveRequest(srcQueueUrl))
      .mapZIOPar(parallelism)(r => receiveBatch(r))
      .filter(_.nonEmpty)
      .mapZIOPar(parallelism)(b => sendBatch(dstQueueUrl, b))
      .filter(_.nonEmpty)
      .mapZIOPar(parallelism)(b => (deleteBatch(srcQueueUrl, b) @@ countMessages).unit)
      .runDrain

  override def download(srcQueueUrl: String, dstDir: File): ZIO[Any, Throwable, Unit] = ???

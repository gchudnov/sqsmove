package com.github.gchudnov.sqsmove.sqs

import com.github.gchudnov.sqsmove.sqs.AwsSqs.makeReceiveRequest
import zio.stream.ZStream
import zio._

/**
 * Parallel SQS Copy
 */
final class ParallelSqsMove(maxConcurrency: Int, parallelism: Int) extends BasicSqsMove(maxConcurrency) {
  import BasicSqsMove.*

  override def copy(srcQueueUrl: String, dstQueueUrl: String): ZIO[Any, Throwable, Unit] =
    ZStream
      .repeat(makeReceiveRequest(srcQueueUrl))
      .mapZIOPar(parallelism)(r => receiveBatch(r))
      .filter(_.nonEmpty)
      .mapZIOPar(parallelism)(b => sendBatch(dstQueueUrl, b))
      .filter(_.nonEmpty)
      .mapZIOPar(parallelism)(b => (deleteBatch(srcQueueUrl, b) @@ countMessages).unit)
      .runDrain
}

package com.github.gchudnov.sqsmove.sqs

import com.github.gchudnov.sqsmove.sqs.AwsSqs.makeReceiveRequest
import com.github.gchudnov.sqsmove.sqs.BasicSqsMove.aspCountMessages
import zio.ZIO
import zio.blocking.Blocking
import zio.logging.Logger
import zio.zmx.metrics.MetricsSyntax

/**
 * Series SQS Copy
 */
final class SerialSqsMove(maxConcurrency: Int, logger: Logger[String]) extends BasicSqsMove(maxConcurrency, logger) {

  override def copy(srcQueueUrl: String, dstQueueUrl: String): ZIO[Blocking, Throwable, Unit] =
    ZIO
      .succeed(makeReceiveRequest(srcQueueUrl))
      .flatMap(r => receiveBatch(r))
      .flatMap(b => sendBatch(dstQueueUrl, b).flatMap(b => deleteBatch(srcQueueUrl, b) @@ aspCountMessages).when(b.nonEmpty))
      .forever
}

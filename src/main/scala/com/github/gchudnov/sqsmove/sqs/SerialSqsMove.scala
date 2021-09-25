package com.github.gchudnov.sqsmove.sqs

import com.github.gchudnov.sqsmove.sqs.AwsSqs.makeReceiveRequest
import com.github.gchudnov.sqsmove.sqs.BasicSqsMove.countMessages
import zio.ZIO

/**
 * Series SQS Copy
 */
final class SerialSqsMove(maxConcurrency: Int) extends BasicSqsMove(maxConcurrency) {

  override def copy(srcQueueUrl: String, dstQueueUrl: String): ZIO[Any, Throwable, Unit] =
    ZIO
      .succeed(makeReceiveRequest(srcQueueUrl))
      .flatMap(r => receiveBatch(r))
      .flatMap(b => sendBatch(dstQueueUrl, b).flatMap(b => deleteBatch(srcQueueUrl, b) @@ countMessages).when(b.nonEmpty))
      .forever
}

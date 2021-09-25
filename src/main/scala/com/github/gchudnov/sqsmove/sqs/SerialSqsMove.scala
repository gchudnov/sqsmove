package com.github.gchudnov.sqsmove.sqs

import com.github.gchudnov.sqsmove.sqs.AwsSqs.makeReceiveRequest
import com.github.gchudnov.sqsmove.sqs.BasicSqsMove.countMessages
import zio.*
import java.io.File

/**
 * Series SQS Move
 */
final class SerialSqsMove(maxConcurrency: Int) extends BasicSqsMove(maxConcurrency):

  override def move(srcQueueUrl: String, dstQueueUrl: String): ZIO[Any, Throwable, Unit] =
    ZIO
      .succeed(makeReceiveRequest(srcQueueUrl))
      .flatMap(r => receiveBatch(r))
      .flatMap(b => sendBatch(dstQueueUrl, b).flatMap(b => deleteBatch(srcQueueUrl, b) @@ countMessages).when(b.nonEmpty))
      .forever

  override def download(srcQueueUrl: String, dstDir: File): ZIO[Any, Throwable, Unit] = ???

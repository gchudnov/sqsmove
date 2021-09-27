package com.github.gchudnov.sqsmove.sqs

import com.github.gchudnov.sqsmove.sqs.AwsSqs.makeReceiveRequest
import com.github.gchudnov.sqsmove.sqs.BasicSqs.countMessages
import zio.*
import java.io.File

/**
 * Serial SQS Move
 */
final class SerialSqs(maxConcurrency: Int, visibilityTimeout: Duration) extends BasicSqs(maxConcurrency):

  override def move(srcQueueUrl: String, dstQueueUrl: String): ZIO[Any, Throwable, Unit] =
    ZIO
      .succeed(makeReceiveRequest(srcQueueUrl))
      .flatMap(r => receiveBatch(r))
      .flatMap(b => sendBatch(dstQueueUrl, b).flatMap(b => deleteBatch(srcQueueUrl, b) @@ countMessages).when(b.nonEmpty))
      .forever

  override def download(srcQueueUrl: String, dstDir: File): ZIO[Any, Throwable, Unit] =
    ZIO
      .succeed(makeReceiveRequest(srcQueueUrl))
      .flatMap(r => receiveBatch(r))
      .flatMap(b => saveBatch(dstDir, b).flatMap(b => deleteBatch(srcQueueUrl, b) @@ countMessages).when(b.nonEmpty))
      .forever

  override def upload(stcDir: File, dstQueueUrl: String): ZIO[Any, Throwable, Unit] = ???

object SerialSqs:
  def layer(maxConcurrency: Int, visibilityTimeout: Duration): ZLayer[Any, Throwable, Has[Sqs]] =
    ZIO.attempt(new SerialSqs(maxConcurrency = maxConcurrency, visibilityTimeout = visibilityTimeout)).toLayer

package com.github.gchudnov.sqsmove.sqs

import com.github.gchudnov.sqsmove.sqs.AwsSqs.makeReceiveRequest
import software.amazon.awssdk.services.sqs.model.Message
import zio.*
import zio.stream.{ ZPipeline, ZStream }

import java.io.File

/**
 * Parallel SQS Move
 */
final class ParallelSqs(maxConcurrency: Int, parallelism: Int, limit: Option[Int], visibilityTimeout: Duration, isDelete: Boolean, clock: Clock) extends BasicSqs(maxConcurrency):
  import BasicSqs.*

  override def move(srcQueueUrl: String, dstQueueUrl: String): ZIO[Any, Throwable, Unit] =
    ZStream
      .repeat(makeReceiveRequest(srcQueueUrl, visibilityTimeoutSec = visibilityTimeout.getSeconds, batchSize = AwsSqs.maxBatchSize))
      .mapZIOPar(parallelism)(r => receiveBatch(r))
      .mapConcat(identity)
      .via(withOptionalLimit)
      .groupedWithin(AwsSqs.maxBatchSize, ParallelSqs.waitBatch)
      .filter(_.nonEmpty)
      .mapZIOPar(parallelism)(b => sendBatch(dstQueueUrl, b))
      .mapZIOPar(parallelism)(b => (deleteBatch(srcQueueUrl, b).when(isDelete).as(b.size) @@ countMessages).unit)
      .runDrain
      .provide(ZLayer.succeed(clock))

  override def download(srcQueueUrl: String, dstDir: File): ZIO[Any, Throwable, Unit] =
    ZStream
      .repeat(makeReceiveRequest(srcQueueUrl, visibilityTimeoutSec = visibilityTimeout.getSeconds, batchSize = AwsSqs.maxBatchSize))
      .mapZIOPar(parallelism)(r => receiveBatch(r))
      .mapConcat(identity)
      .via(withOptionalLimit)
      .groupedWithin(AwsSqs.maxBatchSize, ParallelSqs.waitBatch)
      .filter(_.nonEmpty)
      .mapZIOPar(parallelism)(b => saveBatch(dstDir, b))
      .mapZIOPar(parallelism)(b => (deleteBatch(srcQueueUrl, b).when(isDelete).as(b.size) @@ countMessages).unit)
      .runDrain
      .provide(ZLayer.succeed(clock))

  override def upload(srcDir: File, dstQueueUrl: String): ZIO[Any, Throwable, Unit] =
    ZStream
      .fromIterableZIO(ZIO.fromEither(BasicSqs.listFilesWithoutMetadata(srcDir)))
      .via(withOptionalLimit)
      .grouped(AwsSqs.maxBatchSize)
      .filter(_.nonEmpty)
      .mapZIOPar(parallelism)(b => ZIO.foreach(b)(messageFromFile).flatMap(b => sendBatch(dstQueueUrl, b).as(b.size) @@ countMessages))
      .runDrain
      .provide(ZLayer.succeed(clock))

  private def withOptionalLimit =
    limit.map(n => ZPipeline.take(n)).getOrElse(ZPipeline.identity)

object ParallelSqs:

  def layer(maxConcurrency: Int, parallelism: Int, limit: Option[Int], visibilityTimeout: Duration, isDelete: Boolean): ZLayer[Clock, Throwable, Sqs] = (for
    clock  <- ZIO.service[Clock]
    service = new ParallelSqs(maxConcurrency = maxConcurrency, parallelism = parallelism, limit = limit, visibilityTimeout = visibilityTimeout, isDelete = isDelete, clock = clock)
  yield service).toLayer

  val waitBatch: Duration = Duration.fromMillis(AwsSqs.waitBatchMillis)

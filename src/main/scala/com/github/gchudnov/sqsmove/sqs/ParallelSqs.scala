package com.github.gchudnov.sqsmove.sqs

import com.github.gchudnov.sqsmove.sqs.AwsSqs.makeReceiveRequest
import com.github.gchudnov.sqsmove.util.FileOps
import software.amazon.awssdk.services.sqs.model.Message
import zio.*
import zio.stream.{ ZPipeline, ZStream }

import java.io.File

/**
 * Parallel SQS Move
 */
final class ParallelSqs(maxConcurrency: Int, parallelism: Int, limit: Option[Int], visibilityTimeout: Duration, isDelete: Boolean) extends BasicSqs(maxConcurrency):
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

  override def upload(srcDir: File, dstQueueUrl: String): ZIO[Any, Throwable, Unit] =
    ZStream
      .fromIterableZIO(ZIO.fromEither(BasicSqs.listFilesWithoutMetadata(srcDir)))
      .via(withOptionalLimit)
      .filterZIO(isFileNonEmpty)
      .grouped(AwsSqs.maxBatchSize)
      .filter(_.nonEmpty)
      .mapZIOPar(parallelism)(b => ZIO.foreach(b)(messageFromFile).flatMap(b => sendBatch(dstQueueUrl, b).as(b.size) @@ countMessages))
      .runDrain

  private def withOptionalLimit[In]: ZPipeline[Any, Nothing, In, In] =
    limit.map(n => ZPipeline.take[In](n.toLong)).getOrElse(ZPipeline.identity[In])

object ParallelSqs:

  def layer(maxConcurrency: Int, parallelism: Int, limit: Option[Int], visibilityTimeout: Duration, isDelete: Boolean): ZLayer[Any, Nothing, Sqs] =
    ZLayer.succeed(
      new ParallelSqs(
        maxConcurrency = maxConcurrency,
        parallelism = parallelism,
        limit = limit,
        visibilityTimeout = visibilityTimeout,
        isDelete = isDelete
      )
    )

  val waitBatch: Duration = Duration.fromMillis(AwsSqs.waitBatchMillis)

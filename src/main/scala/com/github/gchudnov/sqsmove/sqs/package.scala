package com.github.gchudnov.sqsmove

import zio.logging.{ Logger, Logging }
import zio.*

package object sqs {
  type Sqs = Has[Sqs.Service]

  object Sqs {
    trait Service {
      def getQueueUrl(name: String): ZIO[Any, Throwable, String]
      def copy(srcQueueUrl: String, dstQueueUrl: String): ZIO[Any, Throwable, Unit]
    }

    val any: ZLayer[Sqs, Nothing, Sqs] =
      ZLayer.environment[Sqs]

    def serial(maxConcurrency: Int): ZLayer[Logging, Throwable, Sqs] = (for {
      logger <- ZIO.service[Logger[String]]
      service = new SerialSqsMove(maxConcurrency, logger)
    } yield service).toLayer

    def parallel(maxConcurrency: Int, parallelism: Int): ZLayer[Logging, Throwable, Sqs] = (for {
      logger <- ZIO.service[Logger[String]]
      service = new ParallelSqsMove(maxConcurrency = maxConcurrency, parallelism = parallelism, logger = logger)
    } yield service).toLayer

    def auto(maxConcurrency: Int, initParallelism: Int): ZLayer[Logging with Has[Clock], Throwable, Sqs] = (for {
      logger <- ZIO.service[Logger[String]]
      clock  <- ZIO.service[Clock]
      service = new AutoSqsMove(maxConcurrency = maxConcurrency, initParallelism = initParallelism, logger = logger, clock = clock)
    } yield service).toLayer
  }

  def getQueueUrl(name: String): ZIO[Sqs, Throwable, String] =
    ZIO.accessM(_.get.getQueueUrl(name))

  def copy(srcQueueUrl: String, dstQueueUrl: String): ZIO[Sqs, Throwable, Unit] =
    ZIO.accessM(_.get.copy(srcQueueUrl, dstQueueUrl))
}

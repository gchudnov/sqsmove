package com.github.gchudnov.sqsmove

import zio.blocking.Blocking
import zio.clock.Clock
import zio.logging.{ Logger, Logging }
import zio.{ Has, ZIO, ZLayer }

package object sqs {
  type Sqs = Has[Sqs.Service]

  object Sqs {
    trait Service {
      def getQueueUrl(name: String): ZIO[Blocking, Throwable, String]
      def copy(srcQueueUrl: String, dstQueueUrl: String): ZIO[Blocking, Throwable, Unit]
    }

    val any: ZLayer[Sqs, Nothing, Sqs] =
      ZLayer.requires[Sqs]

    def serial(maxConcurrency: Int): ZLayer[Logging, Throwable, Sqs] = ZLayer.fromService { logger =>
      new SerialSqsMove(maxConcurrency, logger)
    }

    def parallel(maxConcurrency: Int, parallelism: Int): ZLayer[Logging, Throwable, Sqs] = ZLayer.fromService { logger =>
      new ParallelSqsMove(maxConcurrency = maxConcurrency, parallelism = parallelism, logger = logger)
    }

    def auto(maxConcurrency: Int, initParallelism: Int): ZLayer[Logging with Clock, Throwable, Sqs] = ZLayer.fromServices[Logger[String], Clock.Service, Sqs.Service] {
      case (logger: Logger[String], clock: Clock.Service) =>
        new AutoSqsMove(maxConcurrency = maxConcurrency, initParallelism = initParallelism, logger = logger, clock = clock)
    }

  }

  def getQueueUrl(name: String): ZIO[Sqs with Blocking, Throwable, String] =
    ZIO.accessM(_.get.getQueueUrl(name))

  def copy(srcQueueUrl: String, dstQueueUrl: String): ZIO[Sqs with Blocking, Throwable, Unit] =
    ZIO.accessM(_.get.copy(srcQueueUrl, dstQueueUrl))
}

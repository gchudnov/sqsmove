package com.github.gchudnov.sqsmove

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

    def serial(maxConcurrency: Int): ZLayer[Any, Throwable, Sqs] =
      ZIO.attempt(new SerialSqsMove(maxConcurrency)).toLayer

    def parallel(maxConcurrency: Int, parallelism: Int): ZLayer[Any, Throwable, Sqs] =
      ZIO.attempt(new ParallelSqsMove(maxConcurrency = maxConcurrency, parallelism = parallelism)).toLayer

    def auto(maxConcurrency: Int, initParallelism: Int): ZLayer[Has[Clock], Throwable, Sqs] = (for {
      clock  <- ZIO.service[Clock]
      service = new AutoSqsMove(maxConcurrency = maxConcurrency, initParallelism = initParallelism, clock = clock)
    } yield service).toLayer
  }

  def getQueueUrl(name: String): ZIO[Sqs, Throwable, String] =
    ZIO.accessZIO(_.get.getQueueUrl(name))

  def copy(srcQueueUrl: String, dstQueueUrl: String): ZIO[Sqs, Throwable, Unit] =
    ZIO.accessZIO(_.get.copy(srcQueueUrl, dstQueueUrl))
}

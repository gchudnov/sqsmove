package com.github.gchudnov.sqsmove

import zio.*
import java.io.File

package object sqs:
  type Sqs = Has[Sqs.Service]

  object Sqs:
    trait Service:
      def getQueueUrl(name: String): ZIO[Any, Throwable, String]
      def move(srcQueueUrl: String, dstQueueUrl: String): ZIO[Any, Throwable, Unit]
      def download(srcQueueUrl: String, dstDir: File): ZIO[Any, Throwable, Unit]

    val any: ZLayer[Sqs, Nothing, Sqs] =
      ZLayer.environment[Sqs]

    def serial(maxConcurrency: Int): ZLayer[Any, Throwable, Sqs] =
      ZIO.attempt(new SerialSqsMove(maxConcurrency)).toLayer

    def parallel(maxConcurrency: Int, parallelism: Int): ZLayer[Any, Throwable, Sqs] =
      ZIO.attempt(new ParallelSqsMove(maxConcurrency = maxConcurrency, parallelism = parallelism)).toLayer

    def auto(maxConcurrency: Int, initParallelism: Int): ZLayer[Has[Clock], Throwable, Sqs] = (for
      clock  <- ZIO.service[Clock]
      service = new AutoSqsMove(maxConcurrency = maxConcurrency, initParallelism = initParallelism, clock = clock)
    yield service).toLayer

  def getQueueUrl(name: String): ZIO[Sqs, Throwable, String] =
    ZIO.serviceWith(_.getQueueUrl(name))

  def move(srcQueueUrl: String, dstQueueUrl: String): ZIO[Sqs, Throwable, Unit] =
    ZIO.serviceWith(_.move(srcQueueUrl, dstQueueUrl))

  def download(srcQueueUrl: String, dstDir: File): ZIO[Sqs, Throwable, Unit] =
    ZIO.serviceWith(_.download(srcQueueUrl, dstDir))

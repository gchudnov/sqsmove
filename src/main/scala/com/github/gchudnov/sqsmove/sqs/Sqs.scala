package com.github.gchudnov.sqsmove.sqs

import zio.*
import java.io.File

trait Sqs:
  def getQueueUrl(name: String): ZIO[Any, Throwable, String]
  def move(srcQueueUrl: String, dstQueueUrl: String): ZIO[Any, Throwable, Unit]
  def download(srcQueueUrl: String, dstDir: File): ZIO[Any, Throwable, Unit]

object Sqs:
  def getQueueUrl(name: String): ZIO[Has[Sqs], Throwable, String] =
    ZIO.serviceWith(_.getQueueUrl(name))

  def move(srcQueueUrl: String, dstQueueUrl: String): ZIO[Has[Sqs], Throwable, Unit] =
    ZIO.serviceWith(_.move(srcQueueUrl, dstQueueUrl))

  def download(srcQueueUrl: String, dstDir: File): ZIO[Has[Sqs], Throwable, Unit] =
    ZIO.serviceWith(_.download(srcQueueUrl, dstDir))

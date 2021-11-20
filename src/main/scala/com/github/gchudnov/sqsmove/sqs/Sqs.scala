package com.github.gchudnov.sqsmove.sqs

import zio.*

import java.io.File

trait Sqs:
  def getQueueUrl(name: String): Task[String]
  def move(srcQueueUrl: String, dstQueueUrl: String): Task[Unit]
  def download(srcQueueUrl: String, dstDir: File): Task[Unit]
  def upload(srdDir: File, dstQueueUrl: String): Task[Unit]

object Sqs:
  def getQueueUrl(name: String): RIO[Sqs, String] =
    ZIO.serviceWithZIO[Sqs](_.getQueueUrl(name))

  def move(srcQueueUrl: String, dstQueueUrl: String): RIO[Sqs, Unit] =
    ZIO.serviceWithZIO[Sqs](_.move(srcQueueUrl, dstQueueUrl))

  def download(srcQueueUrl: String, dstDir: File): RIO[Sqs, Unit] =
    ZIO.serviceWithZIO[Sqs](_.download(srcQueueUrl, dstDir))

  def upload(srcDir: File, dstQueueUrl: String): RIO[Sqs, Unit] =
    ZIO.serviceWithZIO[Sqs](_.upload(srcDir, dstQueueUrl))

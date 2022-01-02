package com.github.gchudnov.sqsmove.sqs

import software.amazon.awssdk.services.sqs.model.Message
import zio.*
import zio.stream.ZStream

import java.io.File
import scala.collection.immutable.IndexedSeq

final class AutoSqs(maxConcurrency: Int, initParallelism: Int, limit: Option[Int], visibilityTimeout: Duration, isDelete: Boolean, clock: Clock) extends BasicSqs(maxConcurrency):
  import AwsSqs.*

  type StopPromise = Promise[Option[Throwable], Unit]

  private val autoUpdateTime    = 5.second
  private val autoBatchSize     = 10
  private val autoBatchWaitTime = 1.second
  private val autoQueueMaxSize  = 65536

  override def move(srcQueueUrl: String, dstQueueUrl: String): ZIO[Any, Nothing, Unit] =
    moveWithAutoTune(srcQueueUrl, dstQueueUrl).provide(ZLayer.succeed(clock)).unit

  override def download(srcQueueUrl: String, dstDir: File): ZIO[Any, Throwable, Unit] = ???

  override def upload(srcDir: File, dstQueueUrl: String): ZIO[Any, Throwable, Unit] = ???

  private def moveWithAutoTune(srcQueueUrl: String, dstQueueUrl: String): ZIO[Clock, Nothing, Fiber[Nothing, Unit]] =
    val cName = "auto-consumer"
    val pName = "auto-producer"
    val dName = "auto-deleter"

    val res = for
      cRef <- ZRef.make(initParallelism)
      pRef <- ZRef.make(initParallelism)
      dRef <- ZRef.make(initParallelism)

      messages <- ZQueue.bounded[Message](autoQueueMaxSize)       // input messages to move
      handles  <- ZQueue.bounded[ReceiptHandle](autoQueueMaxSize) // message handles to delete when a message was copied

      csRef <- ZRef.make(List.empty[StopPromise]) // active consumers
      psRef <- ZRef.make(List.empty[StopPromise]) // active producers
      dsRef <- ZRef.make(List.empty[StopPromise]) // active deleters

      f0 <-
        scaleWorkers(cName, cRef, csRef)(
          newConsumer(cName, csRef)(messages, receiveBatch(makeReceiveRequest(srcQueueUrl, visibilityTimeoutSec = visibilityTimeout.getSeconds, batchSize = AwsSqs.maxBatchSize)))
        )
          .schedule(Schedule.spaced(autoUpdateTime))
          .unit
          .fork
      f1 <- scaleWorkers(pName, pRef, psRef)(newProducer(pName, psRef)(messages, handles, sendBatch(dstQueueUrl, _)))
              .schedule(Schedule.spaced(autoUpdateTime))
              .unit
              .fork
      f2 <- scaleWorkers(dName, dRef, dsRef)(newDeleter(dName, dsRef)(handles, deleteBatch(srcQueueUrl, _)))
              .schedule(Schedule.spaced(autoUpdateTime))
              .unit
              .fork

    // TODO: implement controller
    yield f1

    res

  private def scaleWorkers[R, E, A](name: String, nRef: Ref[Int], psRef: Ref[List[StopPromise]])(newWorker: => ZIO[R, E, A]): ZIO[R, E, Unit] =
    for
      n                 <- nRef.get
      _                 <- ZIO.logDebug(s"[$name] n = $n")
      ps                <- psRef.get
      (retains, removes) = ps.splitAt(n)
      _                 <- psRef.set(retains)
      _                 <- ZIO.logDebug(s"[$name] current: ${ps.size}; to_create: ${n - retains.size}; to_delete: ${removes.size};")
      _                 <- ZIO.foreachDiscard(removes)(it => ZIO.logDebug(s"[$name] delete") *> it.fail(None))
      _ <- ZIO.foreachDiscard(List.fill(n - retains.size)(())) { _ =>
             newWorker
           }
    yield ()

  private def newProducer[A, B](name: String, psRef: Ref[List[StopPromise]])(
    inQueue: Queue[A],
    outQueue: Queue[B],
    runEffect: Chunk[A] => ZIO[Any, Throwable, IndexedSeq[B]]
  ): ZIO[Clock, Nothing, Fiber.Runtime[Throwable, Unit]] =
    for
      _ <- ZIO.logDebug(s"[$name] create")
      p <- Promise.make[Option[Throwable], Unit] // promise to cancel
      _ <- psRef.update(xs => p :: xs)
      s1 = ZStream.fromZIOOption(p.await)
      s2 = ZStream
             .fromQueue(inQueue)
             .groupedWithin(autoBatchSize, autoBatchWaitTime)
             .filter(_.nonEmpty)
             .mapZIO(b => runEffect(b))
             .tap(b => ZIO.logDebug(s"[$name] produce size: ${b.size}"))
             .mapZIO(b => outQueue.offerAll(b).unit)
      s3 = s1.mergeTerminateEither(s2)
      f <- (s3.runDrain *> ZIO.logDebug(s"[$name] done")).fork
    yield f

  private def newConsumer[A, B](
    name: String,
    csRef: Ref[List[StopPromise]]
  )(outQueue: Queue[B], runEffect: => ZIO[Any, Throwable, IndexedSeq[B]]): ZIO[Any, Nothing, Fiber.Runtime[Throwable, Unit]] =
    for
      _ <- ZIO.logDebug(s"[$name] create")
      p <- Promise.make[Option[Throwable], Unit] // promise to cancel
      _ <- csRef.update(xs => p :: xs)
      f <- runEffect
             .flatMap(b => ZIO.when(b.nonEmpty)(outQueue.offerAll(b)))
             .forever
             .race(
               p.await.foldZIO(
                 {
                   case Some(t) => ZIO.fail(t)
                   case None    => ZIO.unit
                 },
                 _ => ZIO.unit
               )
             )
             .fork
    yield f

  private def newDeleter[A, B](name: String, dsRef: Ref[List[StopPromise]])(inQueue: Queue[A], runEffect: Chunk[A] => ZIO[Any, Throwable, Int]) =
    for
      _ <- ZIO.logDebug(s"[$name] create")
      p <- Promise.make[Option[Throwable], Unit] // promise to cancel
      _ <- dsRef.update(xs => p :: xs)
      s1 = ZStream.fromZIOOption(p.await)
      s2 = ZStream
             .fromQueue(inQueue)
             .groupedWithin(autoBatchSize, autoBatchWaitTime)
             .filter(_.nonEmpty)
             .mapZIO(b => runEffect(b))
             .tap(n => ZIO.logDebug(s"[$name] delete size: $n"))
      s3 = s1.mergeTerminateEither(s2)
      f <- (s3.runDrain *> ZIO.logDebug(s"[$name] done")).fork
    yield f

object AutoSqs:
  def layer(maxConcurrency: Int, initParallelism: Int, limit: Option[Int], visibilityTimeout: Duration, isDelete: Boolean): ZLayer[Clock, Throwable, Sqs] = (for
    clock <- ZIO.service[Clock]
    service =
      new AutoSqs(maxConcurrency = maxConcurrency, initParallelism = initParallelism, limit = limit, visibilityTimeout = visibilityTimeout, isDelete = isDelete, clock = clock)
  yield service).toLayer

package com.github.gchudnov.sqsmove.sqs

import software.amazon.awssdk.services.sqs.model.Message
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.logging.Logger
import zio.stream.ZStream
import zio.{ Chunk, Fiber, Promise, Queue, Ref, Schedule, ZIO, ZLayer, ZQueue, ZRef }

import scala.collection.immutable.IndexedSeq

final class AutoSqsMove(maxConcurrency: Int, initParallelism: Int, logger: Logger[String], clock: Clock.Service) extends BasicSqsMove(maxConcurrency, logger) {
  import AwsSqs._

  type StopPromise = Promise[Option[Throwable], Unit]

  private val autoUpdateTime    = 5.second
  private val autoBatchSize     = 10
  private val autoBatchWaitTime = 1.second
  private val autoQueueMaxSize  = 65536

  override def copy(srcQueueUrl: String, dstQueueUrl: String): ZIO[Blocking, Nothing, Unit] =
    copyWithAutoTune(srcQueueUrl, dstQueueUrl)
      .provideSomeLayer[Blocking](ZLayer.succeed(clock))
      .unit

  private def copyWithAutoTune(srcQueueUrl: String, dstQueueUrl: String): ZIO[Clock with Blocking, Nothing, Fiber[Nothing, Unit]] = {
    val cName = "auto-consumer"
    val pName = "auto-producer"
    val dName = "auto-deleter"

    val res = for {
      cRef <- ZRef.make(initParallelism)
      pRef <- ZRef.make(initParallelism)
      dRef <- ZRef.make(initParallelism)

      messages <- ZQueue.bounded[Message](autoQueueMaxSize) // input messages to copy
      handles  <- ZQueue.bounded[ReceiptHandle](autoQueueMaxSize) // message handles to delete when a message was copied

      csRef <- ZRef.make(List.empty[StopPromise]) // active consumers
      psRef <- ZRef.make(List.empty[StopPromise]) // active producers
      dsRef <- ZRef.make(List.empty[StopPromise]) // active deleters

      f0 <- scaleWorkers(cName, cRef, csRef)(newConsumer(cName, csRef)(messages, receiveBatch(makeReceiveRequest(srcQueueUrl))))
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

    } yield f1

    res
  }

  private def scaleWorkers[R, E, A](name: String, nRef: Ref[Int], psRef: Ref[List[StopPromise]])(newWorker: => ZIO[R, E, A]): ZIO[R, E, Unit] =
    for {
      n                 <- nRef.get
      _                 <- logger.debug(s"[$name] n = $n")
      ps                <- psRef.get
      (retains, removes) = ps.splitAt(n)
      _                 <- psRef.set(retains)
      _                 <- logger.debug(s"[$name] current: ${ps.size}; to_create: ${n - retains.size}; to_delete: ${removes.size};")
      _                 <- ZIO.foreach_(removes)(it => logger.debug(s"[$name] delete") *> it.fail(None))
      _ <- ZIO.foreach_(List.fill(n - retains.size)(())) { _ =>
             newWorker
           }
    } yield ()

  private def newProducer[A, B](name: String, psRef: Ref[List[StopPromise]])(
    inQueue: Queue[A],
    outQueue: Queue[B],
    runEffect: Chunk[A] => ZIO[Blocking, Throwable, IndexedSeq[B]]
  ): ZIO[Blocking with Any with Clock, Nothing, Fiber.Runtime[Throwable, Unit]] =
    for {
      _ <- logger.debug(s"[$name] create")
      p <- Promise.make[Option[Throwable], Unit] // promise to cancel
      _ <- psRef.update(xs => p :: xs)
      s1 = ZStream.fromEffectOption(p.await)
      s2 = ZStream
             .fromQueue(inQueue)
             .groupedWithin(autoBatchSize, autoBatchWaitTime)
             .filter(_.nonEmpty)
             .mapM(b => runEffect(b))
             .tap(b => logger.debug(s"[$name] prodice size: ${b.size}"))
             .mapM(b => outQueue.offerAll(b).unit)
      s3 = s1.mergeTerminateEither(s2)
      f <- s3.runDrain.andThen(logger.debug(s"[$name] done")).fork
    } yield f

  private def newConsumer[A, B](
    name: String,
    csRef: Ref[List[StopPromise]]
  )(outQueue: Queue[B], runEffect: => ZIO[Blocking, Throwable, IndexedSeq[B]]): ZIO[Blocking, Nothing, Fiber.Runtime[Throwable, Unit]] =
    for {
      _ <- logger.debug(s"[$name] create")
      p <- Promise.make[Option[Throwable], Unit] // promise to cancel
      _ <- csRef.update(xs => p :: xs)
      f <- runEffect
             .flatMap(b => ZIO.when(b.nonEmpty)(outQueue.offerAll(b)))
             .forever
             .race(
               p.await.foldM(
                 {
                   case Some(t) => ZIO.fail(t)
                   case None    => ZIO.unit
                 },
                 _ => ZIO.unit
               )
             )
             .fork
    } yield f

  private def newDeleter[A, B](name: String, dsRef: Ref[List[StopPromise]])(inQueue: Queue[A], runEffect: Chunk[A] => ZIO[Blocking, Throwable, Int]) =
    for {
      _ <- logger.debug(s"[$name] create")
      p <- Promise.make[Option[Throwable], Unit] // promise to cancel
      _ <- dsRef.update(xs => p :: xs)
      s1 = ZStream.fromEffectOption(p.await)
      s2 = ZStream
             .fromQueue(inQueue)
             .groupedWithin(autoBatchSize, autoBatchWaitTime)
             .filter(_.nonEmpty)
             .mapM(b => runEffect(b))
             .tap(n => logger.debug(s"[$name] delete size: $n"))
      s3 = s1.mergeTerminateEither(s2)
      f <- s3.runDrain.andThen(logger.debug(s"[$name] done")).fork
    } yield f
}

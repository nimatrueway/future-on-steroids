package io.github.nimatrueway

import odelay.Timer
import retry.Policy
import retry.Success

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.TimeoutException
import scala.concurrent.duration
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

object futures {
  implicit class RichLazyFuture[A](future: => Future[A]) {
    def timed(implicit ec: ExecutionContext): Future[(FiniteDuration, A)] = {
      val now = System.nanoTime()
      future.map { value =>
        val finished = System.nanoTime()
        FiniteDuration(finished - now, duration.NANOSECONDS) -> value
      }
    }
    def retryOnFailure(policy: Policy)(implicit success: Success[A], ec: ExecutionContext): Future[A] = {
      policy.apply(future)
    }
  }
  implicit class RichFuture[A](future: Future[A]) {
    def timeout(duration: FiniteDuration)(implicit ec: ExecutionContext, timer: Timer): Future[A] = {
      Future.firstCompletedOf(Seq(
        future,
        odelay.Delay(duration) {
          // until they fix https://github.com/softwaremill/odelay/pull/102
          Future.failed(new TimeoutException("Future timed out after [" + duration + "]"))
        }.future.flatten
      ))
    }
  }
  implicit class RichSeqFuture[A, S](futureSeq: Seq[Future[A]]) {
    def parFlatMap[B](parallelism: Int)(fn: A => Future[B])(implicit ec: ExecutionContext): Seq[Future[B]] = {
      if (parallelism > futureSeq.length) {
        futureSeq.map(_.flatMap(fn))
      } else {
        val promises = futureSeq.map(_ => Promise[B]())
        val pool = new ConcurrentLinkedQueue[(Future[A], Promise[B])](futureSeq.zip(promises).asJava)
        def launchNext(): Unit = {
          pool.poll() match {
            case null => ()
            case (futureA, promiseB) =>
              promiseB.completeWith(futureA.flatMap(fn).andThen { case _ =>
                launchNext()
              })
          }
        }

        for (_ <- 1 to parallelism) {
          launchNext()
        }
        promises.map(_.future)
      }
    }
  }

}

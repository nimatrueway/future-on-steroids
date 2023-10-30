package io.github.nimatrueway

import io.github.nimatrueway.futures._
import munit.Compare
import munit.FunSuite
import retry.Success

import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.TimeoutException
import scala.concurrent.blocking
import scala.concurrent.duration._
import scala.util.Try

class futuresTest extends FunSuite {
  private val testTimeout = 5.seconds
  implicit private val compareTimeWithTolerance: Compare[FiniteDuration, FiniteDuration] = (a, b) => (a.toMillis - b.toMillis) < 100

  test("Future.timeout(?) should have no effect if the future is completed on time") {
    val f = Future {
      blocking {
        Thread.sleep(50)
        10
      }
    }

    val r = Await.result(f.timeout(500.millis), testTimeout)
    assertEquals(r, 10)
  }

  test("Future.timeout(X) should have a future failed after X time") {
    val f = Future {
      blocking {
        Thread.sleep(1000)
        10
      }
    }

    val now = System.nanoTime()
    val (measuredDuration, r) = Await.result(f.timeout(500.millis).transform(r => Try(r)).timed, testTimeout)
    val after = System.nanoTime()
    val duration = (after - now).nanoseconds

    assertEquals(duration, 500.millis)
    assertEquals(measuredDuration, 500.millis)

    assert(r.isFailure)
    assertEquals(r.failed.get.getClass.getName, classOf[TimeoutException].getName)
    assertEquals(r.failed.get.getMessage, "Future timed out after [500 milliseconds]")
  }

  test("Future.retry should work") {
    val complete = new AtomicReference(Option.empty[Boolean])
    implicit val success: Success[Unit] = retry.Success(_ => true)
    val f =
      Future {
        while (complete.get().isEmpty) {}
        val value = complete.get().get
        complete.set(None)
        if (value) () else throw new IllegalStateException()
      }
      .retryOnFailure(retry.Directly(max = 3))

    complete.set(Some(false)) // fail for 1st time
    Thread.sleep(100)
    complete.set(Some(false)) // fail for 2st time
    Thread.sleep(100)
    complete.set(Some(true)) // success
    assertEquals(Await.result(f, testTimeout), ())
  }

  test("Seq[Future[x]].parFlatMap(X)(F) should map each element using F with X parallelism") {
    val parallelism = 3
    val started = new AtomicInteger(0)
    val active = new AtomicInteger(0)
    val semaphore = new Semaphore(0)

    val futureSeq = (1 to 10)
      .map(Future.successful)
      .parFlatMap(parallelism) { i =>
        Future {
          started.incrementAndGet()
          active.incrementAndGet()
          blocking {
            semaphore.acquire()
          }
          active.decrementAndGet()
          i * 2
        }
      }

    Thread.sleep(100)
    assertEquals(started.get(), parallelism)
    assertEquals(active.get(), parallelism)

    semaphore.release(1) // complete 1 future

    Thread.sleep(100)
    assertEquals(started.get(), parallelism + 1)
    assertEquals(active.get(), parallelism)

    semaphore.release(9) // complete all futures

    Thread.sleep(100)
    assertEquals(started.get(), futureSeq.length)
    assertEquals(active.get(), 0)

    assertEquals(Await.result(Future.sequence(futureSeq), testTimeout), 2 to 20 by 2)
  }

}

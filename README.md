### Future on steroids

If you do not want to subscribe to Monix, Cats-Effect, or Zio yet you are frustrated with lack of some basic features
on `Future` abstraction this tiny tool may help you.

Usage:

```scala
import io.github.nimatrueway.futures._
import scala.concurrent.Future

  // small niceties
  Future { // some io operation }
      .timeout(3.seconds)                      // timeout after 3 seconds
      .retryOnFailure(retry.Directly(max = 3)) // retry 3 times
      .timed                                   // measure execution time

  // bounded parallelism flatMap
  (1 to 10)
    .map(Future.successful)
    .parFlatMap(3) {                       // maps only 3 elements at a time until their mapped future is complete
      Future { // some io operation }
    }
```

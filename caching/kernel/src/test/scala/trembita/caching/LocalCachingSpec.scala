package trembita.caching

import java.util.concurrent.atomic.AtomicInteger

import cats.effect.{IO, Timer}
import org.scalatest.FlatSpec
import trembita._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class LocalCachingSpec extends FlatSpec {
  implicit val ioTimer: Timer[IO] = IO.timer(ExecutionContext.global)

  "local caching" should "cache values of sequential pipeline" in {
    implicit val caching: Caching[IO, Sequential, Int] =
      Caching.localCaching[IO, Sequential, Int](ExpirationTimeout(5.seconds)).unsafeRunSync()
    val pipeline = Input.sequentialF[IO, Seq].create(IO { 1 to 4 })

    val captore = new AtomicInteger()
    val resultPipeline = pipeline
      .map { i =>
        captore.incrementAndGet()
        i + 1
      }
      .cached("numbers")

    val result = resultPipeline.into(Output.vector).run.unsafeRunSync()
    assert(result == Vector(2, 3, 4, 5))
    assert(captore.get() == 4)

    val result2 = resultPipeline.into(Output.vector).run.unsafeRunSync()
    assert(result2 == Vector(2, 3, 4, 5))
    assert(captore.get() == 4)
  }

  it should "expire sequential pipeline after timeout" in {
    implicit val caching: Caching[IO, Sequential, Int] =
      Caching.localCaching[IO, Sequential, Int](ExpirationTimeout(1.second)).unsafeRunSync()
    val pipeline = Input.sequentialF[IO, Seq].create(IO { 1 to 4 })

    val captore = new AtomicInteger()
    val resultPipeline = pipeline
      .map { i =>
        captore.incrementAndGet()
        i + 1
      }
      .cached("numbers")

    val result = resultPipeline.into(Output.vector).run.unsafeRunSync()
    assert(result == Vector(2, 3, 4, 5))
    assert(captore.get() == 4)

    Thread.sleep(2000)

    val result2 = resultPipeline.into(Output.vector).run.unsafeRunSync()
    assert(result2 == Vector(2, 3, 4, 5))
    assert(captore.get() == 8)
  }

  it should "cache values of parallel pipeline" in {
    implicit val caching: Caching[IO, Parallel, Int] =
      Caching.localCaching[IO, Parallel, Int](ExpirationTimeout(5.seconds)).unsafeRunSync()
    val pipeline = Input.sequentialF[IO, Seq].create(IO { 1 to 4 })

    val captore = new AtomicInteger()
    val resultPipeline = pipeline
      .to[Parallel]
      .map { i =>
        captore.incrementAndGet()
        i + 1
      }
      .cached("numbers")

    val result = resultPipeline.into(Output.vector).run.unsafeRunSync()
    assert(result == Vector(2, 3, 4, 5))
    assert(captore.get() == 4)

    val result2 = resultPipeline.into(Output.vector).run.unsafeRunSync()
    assert(result2 == Vector(2, 3, 4, 5))
    assert(captore.get() == 4)
  }

  it should "expire parallel pipeline after timeout" in {
    implicit val caching: Caching[IO, Parallel, Int] =
      Caching.localCaching[IO, Parallel, Int](ExpirationTimeout(1.second)).unsafeRunSync()
    val pipeline = Input.sequentialF[IO, Seq].create(IO { 1 to 4 })

    val captore = new AtomicInteger()
    val resultPipeline = pipeline
      .to[Parallel]
      .map { i =>
        captore.incrementAndGet()
        i + 1
      }
      .cached("numbers")

    val result = resultPipeline.into(Output.vector).run.unsafeRunSync()
    assert(result == Vector(2, 3, 4, 5))
    assert(captore.get() == 4)

    Thread.sleep(2000)

    val result2 = resultPipeline.into(Output.vector).run.unsafeRunSync()
    assert(result2 == Vector(2, 3, 4, 5))
    assert(captore.get() == 8)
  }
}

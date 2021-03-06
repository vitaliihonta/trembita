package com.examples.spark.streaming

import java.nio.file.FileSystems
import cats.Id
import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all._
import trembita._
import trembita.collections._
import trembita.fsm._
import trembita.spark._
import trembita.spark.streaming._
import org.apache.spark._
import org.apache.spark.sql.{Encoder, Encoders}
import org.apache.spark.streaming.{StreamingContext, Duration => StreamingDuration}
import scala.concurrent.duration._

object FSMSample extends IOApp {
  sealed trait DoorState extends Serializable
  case object Opened     extends DoorState
  case object Closed     extends DoorState

  implicit val doorStateEncoder: Encoder[DoorState] = Encoders.kryo[DoorState]
  implicit val stateEncoder: Encoder[Map[DoorState, Int]] =
    Encoders.kryo[Map[DoorState, Int]]

  def sparkSample(implicit ssc: StreamingContext): IO[Unit] = {
    implicit val timeout: AsyncTimeout = AsyncTimeout(5.minutes)

    val pipeline: DataPipeline[Int, SparkStreaming] =
      Input
        .lift[SparkStreaming]
        .create(Vector.tabulate(5000)(i => scala.util.Random.nextInt() + i))

    val withDoorState =
      pipeline
        .fsmByKey[Int, DoorState, Map[DoorState, Int], Int](getKey = _ % 4)(
          initial = InitialState.pure(FSM.State(Opened, Map.empty))
        )(_.when(Opened) {
          case i if i % 2 == 0 =>
            _.goto(Closed)
              .modify(_.modify(Opened, default = 1)(_ + 1))
              .push(_.apply(Opened) + i)
          case i if i % 4 == 0 => _.stay push (i * 2)
        }.when(Closed) {
            case i if i % 3 == 0 =>
              _.goto(Opened)
                .modify(_.modify(Closed, default = 1)(_ + 1)) spam (_.apply(
                Closed
              ) to 10)
            case i if i % 2 == 0 =>
              _.stay.push(_.values.sum)
          }
          .whenUndefined { i =>
            {
              println(s"Producing nothing..! [#$i]")
              _.goto(Closed).change(Map.empty).dontPush
            }
          })
        .mapK(idTo[IO])
        .map(_ + 1)

    withDoorState
      .tapRepr(_.print())
      .into(Output.start)
      .run
      .flatMap(_ => IO { ssc.awaitTermination() })
  }
  def run(args: List[String]): IO[ExitCode] =
    IO(
      new SparkContext("spark://spark-master:7077", "trembita-spark")
    ).bracket(use = { trembitaSpark: SparkContext =>
        implicit val ssc: StreamingContext = new StreamingContext(trembitaSpark, batchDuration = StreamingDuration(1000))
        val checkpointDir                  = FileSystems.getDefault.getPath(".") resolve "checkpoint"
        ssc.checkpoint(s"file://${checkpointDir.toAbsolutePath}")
        sparkSample
      })(release = spark => IO { spark.stop() })
      .as(ExitCode.Success)
}

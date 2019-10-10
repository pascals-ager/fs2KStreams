package io.pascals.fs2

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import fs2.kafka._
import io.circe.parser._
import io.circe.syntax._
import io.pascals.fs2.domain.Click._
import io.pascals.fs2.utils.Transform

object FS2StreamToKafka extends IOApp {


  def run(args: List[String]): IO[ExitCode] = {

    consumerStream(consumerSettings)
      .evalTap(_.subscribeTo(subscribeTopic))
      .flatMap(_.stream)
      .mapAsync(10){
          committable => IO(decode[Click](committable.record.value))
      }
      .balanceThrough(10000,10)(Transform[IO, Click, ClickPartitioned])
      .balanceThrough(10000,10)(Transform[IO, ClickPartitioned, ClickFlattened])
      .mapAsync(3)(record => IO.pure(record.toOption))
      .flattenOption
      .mapAsync(10){
        record => IO.pure(ProducerRecords.one(ProducerRecord(produceTopic, None, record.asJson.toString)))
      }
      .balanceThrough(10000, 10)(produce(producerSettings))
      .compile.drain.as(ExitCode.Success)

    /* ToDo:
    *   Compositional Joins
    *   Compositional Logging
    *   Compositional Error Handling
    *   Compositional Retry
    *   Compositional Scheduling
    *   Compositional Start/Stop?
    *   Compositional HiveStreaming && Postgres Streaming
    *   Compositional HTTP Streaming Client
    *   Compositional HTTP Streaming Server
    * */

  }
}

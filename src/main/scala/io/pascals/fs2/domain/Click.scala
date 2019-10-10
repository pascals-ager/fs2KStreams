package io.pascals.fs2.domain

import java.sql.{Timestamp => SqlTimestamp}
import java.time.format.DateTimeFormatter
import java.time.{OffsetDateTime, ZoneOffset}

import cats.effect.IO
import fs2.kafka.{Acks, AutoOffsetReset, ConsumerSettings, Deserializer, IsolationLevel, ProducerSettings, Serializer}
import io.circe.Decoder.Result
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.pascals.fs2.utils.Transform

object Click {
  case class SourceAttributes (id: Option[String],
                               origin: Option[String],
                               data: Option[Map[String, String]],
                               external_data: Option[Map[String, String]])

  case class Strategy (`type`: Option[String],
                       parameters: Option[Map[String, String]] )

  case class EventDataStrategy (service: Option[Map[String, String]],
                                result: Option[Map[String, String]])

  case class Click(`type`: String,
                                   id: String,
                                   referenced_event_id: Option[String],
                                   happened: OffsetDateTime,
                                   processed: OffsetDateTime,
                                   tracking_id: String,
                                   source_attributes: SourceAttributes,
                                   event_data: EventDataStrategy)

  case class ClickPartitioned ( `type`: String,
                                       id: String,
                                       referenced_event_id: Option[String],
                                       happened: SqlTimestamp,
                                       processed: SqlTimestamp,
                                       tracking_id: String,
                                       source_attributes: SourceAttributes,
                                       event_data: EventDataStrategy,
                                       year: Int,
                                       month: Int,
                                       day: Int )

  case class ClickFlattened ( `type`: String,
                                             id: String,
                                             referenced_event_id: Option[String],
                                             happened: SqlTimestamp,
                                             processed: SqlTimestamp,
                                             tracking_id: String,
                                             source_id: Option[String],
                                             source_origin: Option[String],
                                             source_data: Option[Map[String, String]],
                                             source_external_data: Option[Map[String, String]],
                                             event_data_service: Option[Map[String, String]],
                                             event_data_result: Option[Map[String, String]],
                                             year: Int,
                                             month: Int,
                                             day: Int )

  implicit val SqlTimestampFormat : Encoder[SqlTimestamp] with Decoder[SqlTimestamp] = new Encoder[SqlTimestamp] with Decoder[SqlTimestamp] {
    override def apply(a: SqlTimestamp): Json = Encoder.encodeLong.apply(a.getTime)

    override def apply(c: HCursor): Result[SqlTimestamp] = Decoder.decodeLong.map(s => new SqlTimestamp(s) ).apply(c)
  }

  implicit val OffsetDateTimeFormat : Encoder[OffsetDateTime] with Decoder[OffsetDateTime] = new Encoder[OffsetDateTime] with Decoder[OffsetDateTime] {
    override def apply(a: OffsetDateTime): Json = Encoder.encodeString.apply(a.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))

    override def apply(c: HCursor): Result[OffsetDateTime] = Decoder.decodeString.map(s => OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME) ).apply(c)
  }

  implicit val SourceAttributesDecoder: Decoder[SourceAttributes] = deriveDecoder[SourceAttributes]
  implicit val StrategyDecoder: Decoder[Strategy] = deriveDecoder[Strategy]
  implicit val EventDataStrategyDecoder: Decoder[EventDataStrategy] = deriveDecoder[EventDataStrategy]
  implicit val IdentificationCarrierDecoder: Decoder[Click] = deriveDecoder[Click]
  implicit val IdentificationCarrierFlattenedEncoder: Encoder[ClickFlattened] = deriveEncoder[ClickFlattened]

  implicit def ClickTsTransformer: Transform[IO, Click, ClickPartitioned]  = ( in: Click ) => {
    val happened_ts = in.happened
    val processed_ts = in.processed
    val happened_utc = happened_ts.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime
    lazy val happened_sql_ts = SqlTimestamp.valueOf(happened_utc)
    val processed_utc = processed_ts.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime
    lazy val processed_sql_ts = SqlTimestamp.valueOf(processed_utc)
    ClickPartitioned(
      in.`type`,
      in.id,
      in.referenced_event_id,
      happened_sql_ts,
      processed_sql_ts,
      in.tracking_id,
      in.source_attributes,
      in.event_data,
      happened_utc.getYear,
      happened_utc.getMonthValue,
      happened_utc.getDayOfMonth)
  }

  implicit def IdsCarrierFlatten: Transform[IO, ClickPartitioned, ClickFlattened] = ( in: ClickPartitioned ) => {
    ClickFlattened(
      in.`type`,
      in.id,
      in.referenced_event_id,
      in.happened,
      in.processed,
      in.tracking_id,
      in.source_attributes.id,
      in.source_attributes.origin,
      in.source_attributes.data,
      in.source_attributes.external_data,
      in.event_data.result,
      in.event_data.service,
      in.year,
      in.month,
      in.day)
  }

  implicit val consumerSettings: ConsumerSettings[IO, Option[String], String] =
    ConsumerSettings(
      keyDeserializer = Deserializer[IO, Option[String]],
      valueDeserializer = Deserializer[IO, String]
    ).withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers("kafka-zk:9092")
      .withGroupId("fs2-kafka-group-dev")
      .withIsolationLevel(IsolationLevel.ReadCommitted)

  implicit val producerSettings: ProducerSettings[IO, Option[String], String] =
    ProducerSettings(
      keySerializer = Serializer[IO, Option[String]],
      valueSerializer = Serializer[IO, String]
    ).withBootstrapServers("kafka-zk:9092")
      .withAcks(Acks.One)

  implicit val subscribeTopic: String = "incoming_clicks"
  implicit val produceTopic: String = "transformed_clicks"
}

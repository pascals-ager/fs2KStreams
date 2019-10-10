package io.pascals.fs2.utils

import fs2.{Chunk, Pipe, Pull, Stream}
import io.circe.Error

trait Bucket[F[_],I] {
  def merge(in: Chunk[Option[I]]) : Chunk[Array[I]]
}

object Bucket {
  def apply[F[_],I]( implicit bucket: Bucket[F, I]): Pipe[F, Either[Error, I], Array[I]] =
    in =>
      in.repeatPull {
        _.uncons.flatMap {
          case Some((hd: Chunk[Either[Error, I]], tl: Stream[F, Either[Error, I]])) => Pull.output(bucket.merge(hd.map {
            case Right(idc: I) => Some(idc)
            case Left(e) => None
          })).as(Some(tl))
          case None => Pull.pure(None)
        }
      }
}

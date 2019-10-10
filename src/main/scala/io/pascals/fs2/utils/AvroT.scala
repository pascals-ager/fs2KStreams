package io.pascals.fs2.utils

import cats.effect.{ConcurrentEffect, ContextShift}
import fs2.{Chunk, Pipe, Pull, Stream}

trait AvroT[F[_], I, O] {
  def convert(in: I) : O
}

object AvroT {
  def apply[F[_], A , B](implicit converter: AvroT[F, A, B], F: ConcurrentEffect[F],
                         context: ContextShift[F]): Pipe[F, A, B] =
    in =>
      in.repeatPull {
        _.uncons.flatMap {
          case Some((hd: Chunk[A], tl: Stream[F, B])) => Pull.output(hd.map {
            record => converter.convert(record)
          }).as(Some(tl))
          case None => Pull.pure(None)
        }
      }
}


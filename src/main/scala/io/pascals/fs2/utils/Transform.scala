package io.pascals.fs2.utils

import cats.effect.{ConcurrentEffect, ContextShift}
import fs2.{Chunk, Pipe, Pull, Stream}
import io.circe.Error

trait Transform[F[_], I, O] {
  def transformation(in: I) : O
}

object Transform {
  def apply[F[_], A , B](implicit transformer: Transform[F, A, B], F: ConcurrentEffect[F],
  context: ContextShift[F]): Pipe[F, Either[Error, A], Either[Error, B]] =
    in =>
      in.repeatPull {
        _.uncons.flatMap {
          case Some((hd: Chunk[Either[Error, A]], tl: Stream[F, Either[Error, A]])) => Pull.output(hd.map {
            case Right(idc: A) => Right(transformer.transformation(idc))
            case Left(e) => Left(e)
          }).as(Some(tl))
          case None => Pull.pure(None)
        }
      }
}

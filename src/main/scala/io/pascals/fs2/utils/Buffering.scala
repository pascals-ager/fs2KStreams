package io.pascals.fs2.utils

import cats.effect.Concurrent
import fs2.Stream
import fs2.concurrent.Queue

class Buffering[F[_]](q1: Queue[F, Int], q2: Queue[F, Int])(implicit F: Concurrent[F]) {
  def start: Stream[F, Unit] =
    Stream(
      Stream.range(0, 1000).covary[F].through(q1.enqueue),
      q1.dequeue.through(q2.enqueue),
      //.map won't work here as you're trying to map a pure value with a side effect. Use `evalMap` instead.
      q2.dequeue.evalMap(n => F.delay(println(s"Pulling out $n from Queue #2")))
    ).parJoin(3)
}


package continuum

import org.scalatest.propspec.AnyPropSpec
import org.scalatest.matchers.should.Matchers

class DiscreteSpec
  extends AnyPropSpec
  with Matchers {

  property("Int next and prev step by one and stop at the domain edges") {
    Discrete.DiscreteInt.next(5) should be (Some(6))
    Discrete.DiscreteInt.prev(5) should be (Some(4))
    Discrete.DiscreteInt.next(Int.MaxValue) should be (None)
    Discrete.DiscreteInt.prev(Int.MinValue) should be (None)
  }

  property("Long next and prev step by one and stop at the domain edges") {
    Discrete.DiscreteLong.next(5L) should be (Some(6L))
    Discrete.DiscreteLong.prev(5L) should be (Some(4L))
    Discrete.DiscreteLong.next(Long.MaxValue) should be (None)
    Discrete.DiscreteLong.prev(Long.MinValue) should be (None)
  }

  property("the byte array successor is the array extended with a zero byte") {
    val a = Array[Byte](1, 2, 3)
    val next = Discrete.DiscreteByteArray.next(a).get
    next should equal (Array[Byte](1, 2, 3, 0))
    Discrete.ByteArrayOrdering.lt(a, next) should be (true)
  }

  property("the byte array predecessor exists only for arrays ending in a zero byte") {
    Discrete.DiscreteByteArray.prev(Array[Byte](1, 2, 3, 0)).get should equal (Array[Byte](1, 2, 3))
    Discrete.DiscreteByteArray.prev(Array[Byte](1, 2, 3)) should be (None)
    Discrete.DiscreteByteArray.prev(Array.empty[Byte]) should be (None)
  }

  property("the byte array ordering is unsigned lexicographic") {
    val ord = Discrete.ByteArrayOrdering
    ord.lt(Array[Byte](1), Array[Byte](2)) should be (true)
    ord.lt(Array[Byte](1), Array[Byte](-1)) should be (true) // -1 is 0xFF, unsigned 255
    ord.lt(Array[Byte](1), Array[Byte](1, 0)) should be (true)
    ord.lt(Array.empty[Byte], Array[Byte](0)) should be (true)
    ord.equiv(Array[Byte](1, 2), Array[Byte](1, 2)) should be (true)
  }

  property("byte array intervals work end to end") {
    implicit val ord: Ordering[Array[Byte]] = Discrete.ByteArrayOrdering

    val interval = Interval.closedOpen(Array[Byte](1), Array[Byte](2))
    interval(Array[Byte](1)) should be (true)
    interval(Array[Byte](1, 5)) should be (true)
    interval(Array[Byte](2)) should be (false)

    val (l, u) = interval.normalize
    l match {
      case NormalizedBound.Value(a) => a should equal (Array[Byte](1))
      case other => fail("unexpected lower bound: " + other)
    }
    u match {
      case NormalizedBound.Value(a) => a should equal (Array[Byte](2))
      case other => fail("unexpected upper bound: " + other)
    }
  }
}

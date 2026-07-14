package continuum

import org.scalatest.propspec.AnyPropSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import continuum.test.Generators

class IntervalSpec extends AnyPropSpec with ScalaCheckPropertyChecks with Matchers with Generators {

  /**
   * intersection properties
   */

  property("intervals intersect if they overlap") {
    forAll { (a: Int, b: Int, c: Int, d: Int) =>
      val bounds = List(a, b, c, d).sorted
      val al = bounds(0)
      val bl = bounds(1)
      val au = bounds(2)
      val bu = bounds(3)

      // unbounded above & closed
      Interval.atLeast(al) intersects Interval.closed(bl, bu) should be(true)

      // closed & closed
      Interval.closed(al, au) intersects Interval.closed(bl, bu) should be(true)

      whenever(bl < au) {

        whenever(al < au) {
          // open & closed
          Interval.open(al, au) intersects Interval.closed(bl, bu) should be(true)
        }

        whenever(bl < bu) {
          // closed & open
          Interval.closed(al, au) intersects Interval.open(bl, bu) should be(true)
        }

        whenever(al < au && bl < bu) {
          // open & open
          Interval.open(al, au) intersects Interval.open(bl, bu) should be(true)
        }
      }
    }
  }

  property("If intervals contain a point in common, then the intervals intersect") {
    forAll(genIntervalRange, genIntervalRange) { (a: Interval[Int], b: Interval[Int]) =>
      whenever(a.toRange.nonEmpty && b.toRange.nonEmpty) {
        (a intersect b).fold(false)(_.toRange.nonEmpty) should equal(
          a.toRange.intersect(b.toRange).nonEmpty
        )
      }
    }
  }

  property("interval intersection is commutative") {
    forAll { (a: Interval[Int], b: Interval[Int]) =>
      a intersect b should equal(b intersect a)
    }
  }

  property("interval intersection is associative") {
    forAll { (a: Interval[Int], b: Interval[Int], c: Interval[Int]) =>
      (a intersect b).flatMap(_ intersect c) should equal((b intersect c).flatMap(_ intersect a))
    }
  }

  property("interval intersection is idempotent") {
    forAll { (interval: Interval[Int]) =>
      interval intersect interval should equal(Some(interval))
    }
  }

  property("interval intersection identity") {
    forAll { (interval: Interval[Int]) =>
      val full = Interval.full[Int]
      interval intersect full should equal(Some(interval))
    }
  }

  /**
   * union laws
   */

  property("intervals union if they overlap or are tangent.") {
    forAll { (a: Interval[Int], b: Interval[Int]) =>
      (a intersects b) || (a tangents b) should equal(a unions b)
    }
  }

  property("interval union is commutative") {
    forAll { (a: Interval[Int], b: Interval[Int]) =>
      a union b should equal(b union a)
    }
  }

  property("interval union is idempotent") {
    forAll { (interval: Interval[Int]) =>
      interval union interval should equal(Some(interval))
    }
  }

  property("interval union is dominated by the full interval") {
    forAll { (interval: Interval[Int]) =>
      val full = Interval.full[Int]
      full union interval should equal(Some(full))
    }
  }

  /**
   * intersection & union laws
   */

  property("interval union absorbtion") {
    forAll { (a: Interval[Int], b: Interval[Int]) =>
      whenever(a intersects b) {
        (a intersect b).get union a should equal(Some(a))
      }
    }
  }

  /**
   * difference
   */

  property("the difference of two intervals will not contain points in in either interval.") {
    forAll { (a: Interval[Int], b: Interval[Int], point: Int) =>
      val difference = a difference b
      val contains = difference.exists(_.apply(point))
      if (a(point)) {
        if (b(point)) contains should be(false)
        else contains should be(true)
      } else contains should be(false)
    }
  }

  property("interval difference zero") {
    forAll { (interval: Interval[Int]) =>
      interval difference interval should equal(IntervalSet.empty[Int])
    }
  }

  property("interval difference identity") {
    forAll { (interval: Interval[Int]) =>
      val full = Interval.full[Int]
      interval difference full should equal(IntervalSet.empty[Int])
    }
  }

  /**
   * normalize
   */

  property("normalize converts bounds to closed-open form") {
    import NormalizedBound.{Empty, Unbounded, Value}
    Interval.closed(1, 5).normalize should be((Value(1), Value(6)))
    Interval.open(1, 5).normalize should be((Value(2), Value(5)))
    Interval.closedOpen(1, 5).normalize should be((Value(1), Value(5)))
    Interval.openClosed(1, 5).normalize should be((Value(2), Value(6)))
    Interval.atLeast(5).normalize should be((Value(5), Unbounded))
    Interval.atMost(5).normalize should be((Unbounded, Value(6)))
    Interval.all[Int].normalize should be((Unbounded, Unbounded))
  }

  property("normalize distinguishes empty-in-domain from unbounded at the domain edges") {
    import NormalizedBound.{Empty, Unbounded, Value}
    Interval.greaterThan(Int.MaxValue).normalize should be((Empty, Unbounded))
    Interval.closed(0, Int.MaxValue).normalize should be((Value(0), Unbounded))
    Interval.atLeast(Int.MaxValue).normalize should be((Value(Int.MaxValue), Unbounded))
  }

  /**
   * toRange
   */

  property("toRange matches normalize on ranges which fit in Ints") {
    import NormalizedBound.Value
    forAll(genIntervalRange) { (interval: Interval[Int]) =>
      val range = interval.toRange
      interval.normalize match {
        case (Value(l), Value(u)) =>
          range.start should be(l)
          range.end should be(if (range.isInclusive) u - 1 else u)
        case _ => succeed
      }
    }
  }

  property("toRange rejects bounds which do not fit exactly in an Int") {
    an[IllegalArgumentException] should be thrownBy Interval.closed(0L, 5000000000L).toRange
    an[IllegalArgumentException] should be thrownBy Interval.atMost(Long.MinValue).toRange
    an[IllegalArgumentException] should be thrownBy Interval.closed(1.0, 2.5).toRange
    Interval.closed(1L, 5L).toRange should equal(Range.inclusive(1, 5))
  }
}

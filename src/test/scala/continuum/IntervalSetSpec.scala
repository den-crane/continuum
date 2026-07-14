package continuum

import org.scalatest.propspec.AnyPropSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import continuum.test.Generators

class IntervalSetSpec
    extends AnyPropSpec
    with ScalaCheckPropertyChecks
    with Matchers
    with Generators {

  property("An interval set should contain all of its constituent intervals") {
    forAll { (intervals: List[Interval[Int]]) =>
      val intervalSet = IntervalSet(intervals: _*)
      intervals.forall(intervalSet.encloses) should be(true)
    }
  }

  property("An interval set coalesces its constituent intervals") {
    forAll { (intervals: List[Interval[Int]]) =>
      val intervalSet = IntervalSet(intervals: _*)
      intervals.size should be >= (intervalSet.size)
      for {
        a <- intervalSet
        b <- intervalSet if a != b
      } a unions b should be(false)
    }
  }

  property("An interval set does not contain an interval in its difference") {
    forAll { (set: IntervalSet[Int], interval: Interval[Int]) =>
      (set - interval) encloses interval should be(false)
    }
  }

  property(
    "An interval set intersected with an interval should contain only intervals in common."
  ) {
    forAll { (set: IntervalSet[Int], interval: Interval[Int]) =>
      val intersection = set intersect interval
      forAll { (i: Interval[Int]) =>
        if (set.encloses(i) && interval.encloses(i)) intersection.encloses(i) should be(true)
        else intersection.encloses(i) should be(false)
      }
    }
  }

  property(
    "An interval set intersected with an interval set should contain only intervals in common."
  ) {
    forAll { (a: IntervalSet[Int], b: IntervalSet[Int]) =>
      val intersection = a intersect b
      forAll { (i: Interval[Int]) =>
        if (a.encloses(i) && b.encloses(i)) intersection.encloses(i) should be(true)
        else intersection.encloses(i) should be(false)
      }
    }
  }

  property("the intervals view exposes members with strict set semantics") {
    forAll { (set: IntervalSet[Int], interval: Interval[Int]) =>
      set.intervals.contains(interval) should be(set.iterator.contains(interval))
    }
  }

  property("coverage is encloses; membership is the intervals view") {
    val set = IntervalSet(Interval.closed(0, 10))
    set.intervals.contains(Interval.closed(0, 10)) should be(true)
    set.intervals.contains(Interval.closed(2, 3)) should be(false)
    set.encloses(Interval.closed(0, 10)) should be(true)
    set.encloses(Interval.closed(2, 3)) should be(true)
    set.encloses(Interval.closed(5, 15)) should be(false)
    set.encloses(Interval.closed(20, 30)) should be(false)
  }

  property("An interval set encloses an interval iff one of its intervals encloses it") {
    forAll { (set: IntervalSet[Int], interval: Interval[Int]) =>
      set.encloses(interval) should be(set.exists(_ encloses interval))
    }
  }

  property("An interval set contains the points of its intervals") {
    forAll { (set: IntervalSet[Int], point: Int) =>
      set.containsPoint(point) should be(set.exists(_(point)))
    }
  }

  property("An interval set intersects an interval iff any of its intervals intersect it") {
    forAll { (set: IntervalSet[Int], interval: Interval[Int]) =>
      set.intersects(interval) should be(set.exists(_ intersects interval))
    }
  }

  property("An interval set intersects an interval it encloses, regardless of ordering") {
    val set = IntervalSet(Interval.closed(0, 10))
    set.intersects(Interval.closed(5, 6)) should be(true)
    set.intersects(Interval.closed(0, 10)) should be(true)
    set.intersects(Interval.closed(-5, 0)) should be(true)
    set.intersects(Interval.closed(10, 15)) should be(true)
    set.intersects(Interval.closed(20, 30)) should be(false)
    set.intersects(Interval.closed(-30, -20)) should be(false)
  }

  property("An empty interval set intersects nothing") {
    forAll { (interval: Interval[Int]) =>
      IntervalSet.empty[Int].intersects(interval) should be(false)
    }
  }

  property("the complement covers exactly the points not covered") {
    forAll { (set: IntervalSet[Int], point: Int) =>
      set.complement.containsPoint(point) should be(!set.containsPoint(point))
    }
  }

  property("the complement is an involution") {
    forAll { (set: IntervalSet[Int]) =>
      set.complement.complement should equal(set)
    }
  }

  property("the complement of the empty set is the full set") {
    IntervalSet.empty[Int].complement should equal(IntervalSet(Interval.all[Int]))
    IntervalSet(Interval.all[Int]).complement should equal(IntervalSet.empty[Int])
  }

  property("a set unioned with its gaps spans without holes") {
    forAll { (set: IntervalSet[Int]) =>
      whenever(set.nonEmpty) {
        (set ++ set.gaps) should equal(IntervalSet(set.spanOption.get))
      }
    }
  }

  property("gaps are disjoint from the set") {
    forAll { (set: IntervalSet[Int]) =>
      set.gaps.intersect(set) should equal(IntervalSet.empty[Int])
    }
  }

  property("from builds the same set as varargs construction") {
    forAll { (intervals: List[Interval[Int]]) =>
      IntervalSet.from(intervals) should equal(IntervalSet(intervals: _*))
      IntervalSet.from(intervals.iterator) should equal(IntervalSet(intervals: _*))
    }
  }

  property("points enumerates the covered points of all intervals in ascending order") {
    val set = IntervalSet(Interval.closed(1, 3)) + Interval.closed(10, 12)
    set.points.toList should equal(List(1, 2, 3, 10, 11, 12))
    IntervalSet.empty[Int].points.toList should equal(Nil)
  }

  property("The span of an interval set  all intervals in the interval set.") {
    forAll { (set: IntervalSet[Int]) =>
      val span = set.spanOption
      forAll { (i: Interval[Int]) =>
        if (set.encloses(i)) span.get.encloses(i) should be(true)
      }
    }
  }
}

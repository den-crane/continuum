package continuum

import org.scalatest.propspec.AnyPropSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import continuum.test.Generators

class CutSpec extends AnyPropSpec with ScalaCheckPropertyChecks with Matchers with Generators {

  val ord: Ordering[Cut[Int]] = Cut.ordering[Int]

  property("the cuts around a value are ordered") {
    forAll { (v: Int) =>
      ord.lt(Cut.BelowAll, Cut.BelowValue(v)) should be(true)
      ord.lt(Cut.BelowValue(v), Cut.AboveValue(v)) should be(true)
      ord.lt(Cut.AboveValue(v), Cut.AboveAll) should be(true)
    }
  }

  property("the cuts of distinct values are ordered by value") {
    forAll { (a: Int, b: Int) =>
      whenever(a < b) {
        ord.lt(Cut.AboveValue(a), Cut.BelowValue(b)) should be(true)
      }
    }
  }

  property("the cut ordering is antisymmetric") {
    forAll { (a: Cut[Int], b: Cut[Int]) =>
      math.signum(ord.compare(a, b)) should be(-math.signum(ord.compare(b, a)))
    }
  }

  property("the cut ordering is transitive") {
    forAll { (a: Cut[Int], b: Cut[Int], c: Cut[Int]) =>
      val sorted = List(a, b, c).sorted(ord)
      ord.lteq(sorted(0), sorted(1)) should be(true)
      ord.lteq(sorted(1), sorted(2)) should be(true)
      ord.lteq(sorted(0), sorted(2)) should be(true)
    }
  }

  property("the cut ordering is consistent with equality") {
    forAll { (a: Cut[Int], b: Cut[Int]) =>
      (ord.compare(a, b) == 0) should be(a == b)
    }
  }

  property("a value is contained by an interval iff it lies between its cuts") {
    forAll { (interval: Interval[Int], point: Int) =>
      val between = ord.lteq(interval.lower, Cut.BelowValue(point)) &&
        ord.lteq(Cut.AboveValue(point), interval.upper)
      interval(point) should be(between)
    }
  }
}

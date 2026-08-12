package continuum.test

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Shrink, Gen, Arbitrary}

import continuum.{Cut, Interval, IntervalSet}

trait Generators {

  implicit def arbCut[T: Arbitrary]: Arbitrary[Cut[T]] =
    Arbitrary(
      Gen.frequency(
        4 -> arbitrary[T].map(Cut.BelowValue(_): Cut[T]),
        4 -> arbitrary[T].map(Cut.AboveValue(_): Cut[T]),
        1 -> Gen.const(Cut.BelowAll: Cut[T]),
        1 -> Gen.const(Cut.AboveAll: Cut[T])
      )
    )

  implicit def arbInterval[T: Arbitrary: Ordering]: Arbitrary[Interval[T]] = Arbitrary {
    for {
      a <- arbitrary[Cut[T]]
      b <- arbitrary[Cut[T]] if Interval.validate(a, b) || Interval.validate(b, a)
    } yield if (Interval.validate(a, b)) Interval(a, b) else Interval(b, a)
  }

  /**
   * Generates arbitrary Interval[Int]s. This is functionally the same as the generic Interval
   * generator, but it is more efficient at generating valid intervals. It will automatically be
   * used by the ScalaCheck framework if both are in scope.
   */
  implicit def arbIntInterval: Arbitrary[Interval[Int]] = Arbitrary {
    def genUpperAbove(lower: Cut[Int]): Gen[Cut[Int]] = lower match {
      case Cut.BelowAll =>
        Gen.frequency(
          4 -> arbitrary[Int].map(Cut.BelowValue(_): Cut[Int]),
          4 -> arbitrary[Int].map(Cut.AboveValue(_): Cut[Int]),
          1 -> Gen.const(Cut.AboveAll: Cut[Int])
        )
      case Cut.BelowValue(l) =>
        Gen.frequency(
          4 -> Gen.choose(l, Int.MaxValue).map(Cut.AboveValue(_): Cut[Int]),
          4 -> (if (l == Int.MaxValue) Gen.const(Cut.AboveAll: Cut[Int])
                else Gen.choose(l + 1, Int.MaxValue).map(Cut.BelowValue(_): Cut[Int])),
          1 -> Gen.const(Cut.AboveAll: Cut[Int])
        )
      case Cut.AboveValue(l) =>
        Gen.frequency(
          8 -> (if (l == Int.MaxValue) Gen.const(Cut.AboveAll: Cut[Int])
                else
                  Gen
                    .choose(l + 1, Int.MaxValue)
                    .flatMap(u =>
                      Gen.oneOf(Cut.BelowValue(u): Cut[Int], Cut.AboveValue(u): Cut[Int])
                    )),
          1 -> Gen.const(Cut.AboveAll: Cut[Int])
        )
      case Cut.AboveAll => Gen.const(Cut.AboveAll)
    }
    for {
      lower <- Gen.frequency(
        4 -> arbitrary[Int].map(Cut.BelowValue(_): Cut[Int]),
        4 -> arbitrary[Int].map(Cut.AboveValue(_): Cut[Int]),
        1 -> Gen.const(Cut.BelowAll: Cut[Int])
      )
      upper <- genUpperAbove(lower)
    } yield Interval(lower, upper)
  }

  implicit def shrinkInterval[T: Shrink: Ordering]: Shrink[Interval[T]] = Shrink.withLazyList {
    interval =>
      def shrinkCut(cut: Cut[T]): LazyList[Cut[T]] = cut match {
        case Cut.BelowValue(v) => Shrink.shrink(v).to(LazyList).map(Cut.BelowValue(_))
        case Cut.AboveValue(v) => Shrink.shrink(v).to(LazyList).map(Cut.AboveValue(_))
        case _                 => LazyList.empty
      }
      for {
        l <- shrinkCut(interval.lower)
        u <- shrinkCut(interval.upper)
        if Interval.validate(l, u)
      } yield Interval(l, u)
  }

  implicit def arbRange: Arbitrary[Range] = Arbitrary {
    Gen.sized { size =>
      for {
        lower <- arbitrary[Int] if lower + size >= lower
      } yield Range.inclusive(lower, lower + size)
    }
  }

  /**
   * Generates ranges which can be converted to a [[scala.collection.immutable.Range]] with the
   * .toRange method. As opposed to the arbitrary Int interval generator, this ensures the created
   * ranges are of a reasonable size.
   */
  def genIntervalRange: Gen[Interval[Int]] = {
    def genOpen: Gen[Interval[Int]] = Gen.sized { size =>
      for {
        lower <- arbitrary[Int] if lower + size > lower
      } yield Interval.open(lower, lower + size)
    }

    def genClosed: Gen[Interval[Int]] = Gen.sized { size =>
      for {
        lower <- arbitrary[Int] if lower + size >= lower
      } yield Interval.closed(lower, lower + size)
    }

    def genOpenClosed: Gen[Interval[Int]] = Gen.sized { size =>
      for {
        lower <- arbitrary[Int] if lower + size > lower
      } yield Interval.openClosed(lower, lower + size)
    }

    def genClosedOpen: Gen[Interval[Int]] = Gen.sized { size =>
      for {
        lower <- arbitrary[Int] if lower + size > lower
      } yield Interval.closedOpen(lower, lower + size)
    }

    def closedUnbounded: Gen[Interval[Int]] =
      Gen.sized(size => Interval.atLeast(Int.MaxValue - size))

    def openUnbounded: Gen[Interval[Int]] =
      Gen.sized(size => Interval.greaterThan(Int.MaxValue - size))

    def unboundedClosed: Gen[Interval[Int]] =
      Gen.sized(size => Interval.atMost(Int.MinValue + size))

    def unboundedOpen: Gen[Interval[Int]] =
      Gen.sized(size => Interval.lessThan(Int.MinValue + size))

    Gen.frequency(
      4 -> genOpen,
      4 -> genClosed,
      4 -> genOpenClosed,
      4 -> genClosedOpen,
      1 -> closedUnbounded,
      1 -> openUnbounded,
      1 -> unboundedClosed,
      1 -> unboundedOpen
    )
  }

  implicit def arbIntervalSet[T: Arbitrary: Ordering]: Arbitrary[IntervalSet[T]] =
    Arbitrary(
      for (intervals <- arbitrary[Array[Interval[T]]]) yield IntervalSet(intervals.toSeq: _*)
    )

  /**
   * Generates arbitrary interval sets of Ints using the more efficient Int interval generator.
   */
  implicit def arbIntIntervalSet: Arbitrary[IntervalSet[Int]] =
    Arbitrary(
      for (intervals <- arbitrary[Array[Interval[Int]]]) yield IntervalSet(intervals.toSeq: _*)
    )
}

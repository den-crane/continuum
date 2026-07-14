# continuum

continuum is a library for working with intervals over continuous, total-ordered domains in Scala. The functionality is similar to Guava's [Range and RangeSet](https://github.com/google/guava/wiki/RangesExplained) libraries. Intervals may be grouped into interval sets which automatically coalesce connected intervals.

Intervals can be created over any element type with an implicit `Ordering` (`Int`, `Long`, `String`, `Double`, `java.time` types via an ordering, byte arrays via `Discrete.ByteArrayOrdering`, ...).

Release notes and the 0.4 → 0.5.0 migration guide are in [CHANGELOG.md](CHANGELOG.md).

## Requirements

- Scala 2.13.18 or Scala 3.3 LTS (cross-built; migrated from 2.11.8 by Claude Code)
- sbt 1.11.7 or later

```bash
sbt +test          # run the test suite for both Scala versions
sbt scalafmtAll    # format sources
```

## Interval

An interval is a non-empty, two-sided bound over a continuous, infinite, total-ordered set of values. An interval contains all values between its lower and upper bound. Additionally, the upper or lower bound of the interval may be unbounded, in which case the interval contains all values above or below, respectively. Intervals provide a rich interface of constructors and set-like operations:

```scala
scala> import continuum.Interval
import continuum.Interval

// Intervals can be closed or open on each side
scala> Interval.closedOpen(10, 20)
res0: continuum.Interval[Int] = [10, 20)

// Intervals can be made from any element with an Ordering
scala> Interval.closed("bar", "baz")
res1: continuum.Interval[String] = [bar, baz]

// Intervals can be unbounded above or below
scala> Interval.greaterThan(19.68)
res2: continuum.Interval[Double] = (19.68, ∞)

scala> Interval.atMost(-42)
res3: continuum.Interval[Int] = (-∞, -42]

scala> Interval.all[Int]
res4: continuum.Interval[Int] = (-∞, ∞)

// Intervals may be a single point
scala> Interval.point(19)
res5: continuum.Interval[Int] = [19]

scala> Interval.closed(19, 19)
res6: continuum.Interval[Int] = [19]

// Intervals may not be empty
scala> Interval.openClosed(1, 1)
java.lang.IllegalArgumentException

scala> Interval.open(1, 1)
java.lang.IllegalArgumentException

// Tuples may be implicitly converted to Intervals
scala> val fromTuple: Interval[String] = ("a", "z")
fromTuple: continuum.Interval[String] = [a, z)

// Ranges may be implicitly converted to an Interval
scala> val fromRange: Interval[Int] = 1 until 10
fromRange: continuum.Interval[Int] = [1, 10)

// and converted explicitly back to a Range
scala> fromRange.toRange
res9: Range = Range 1 until 10

// An interval is a predicate on points
scala> val interval = Interval.closed(0, 10)
interval: continuum.Interval[Int] = [0, 10]

scala> interval(5)
res10: Boolean = true

// and can be tested against other intervals
scala> Interval.closed(0, 10) encloses Interval.open(2, 3)
res11: Boolean = true

// Intervals may be intersected
scala> Interval.open("aardvark", "camel") intersect Interval.closed("bear", "deer")
res12: Option[continuum.Interval[String]] = Some([bear, camel))

// or unioned
scala> Interval.open("aardvark", "camel") union Interval.closed("bear", "deer")
res13: Option[continuum.Interval[String]] = Some((aardvark, deer])

// or the minimum spanning interval
scala> Interval.lessThan(0) span Interval.open(20, 25)
res14: continuum.Interval[Int] = (-∞, 25)

// The difference of two intervals is an IntervalSet of 0, 1, or 2 intervals
scala> Interval.closed(0, 10) difference Interval.closed(4, 5)
res15: continuum.IntervalSet[Int] = IntervalSet([0, 4), (5, 10])
```

Internally an interval is a pair of totally-ordered `Cut`s (`BelowAll < BelowValue(a) < AboveValue(a) < ... < AboveAll`), so every operation above is a plain comparison of cuts.

## IntervalSet

An interval set is a set which contains 0 or more intervals. Connected intervals are automatically coalesced, so at all times an interval set contains only the minimum number of intervals necessary. Interval sets are immutable and persistent.

An interval set is deliberately **not** a Scala `Set[Interval[T]]` — its operations are geometric (adding coalesces, removing clips), which cannot satisfy the `Set` laws. It is an `Iterable` of its members in sorted order, and the member set with strict element semantics is available via `.intervals`.

```scala
scala> import continuum.Interval; import continuum.IntervalSet
import continuum.Interval
import continuum.IntervalSet

scala> IntervalSet(Interval.open(10, 20))
res0: continuum.IntervalSet[Int] = IntervalSet((10, 20))

// Adding coalesces connected intervals
scala> IntervalSet(Interval.open(10, 20)) + Interval.closed(15, 25)
res1: continuum.IntervalSet[Int] = IntervalSet((10, 25])

scala> IntervalSet(Interval.open(10, 20)) + Interval.closed(25, 30)
res2: continuum.IntervalSet[Int] = IntervalSet((10, 20), [25, 30])

// Removing clips members
scala> IntervalSet(Interval.all[Int]) - Interval.closed(32, 35)
res3: continuum.IntervalSet[Int] = IntervalSet((-∞, 32), (35, ∞))

// Coverage queries: points and intervals
scala> IntervalSet(Interval.closed(0, 10)).containsPoint(5)   // or set(5)
res4: Boolean = true

scala> IntervalSet(Interval.closed(0, 10)) encloses Interval.closed(2, 3)
res5: Boolean = true

// Strict membership goes through the member view
scala> IntervalSet(Interval.closed(0, 10)).intervals.contains(Interval.closed(2, 3))
res6: Boolean = false

// Geometric set algebra: intersect, union, difference
scala> IntervalSet(1 to 10) intersect IntervalSet(5 to 15)
res7: continuum.IntervalSet[Int] = IntervalSet([5, 10])

scala> IntervalSet(Interval.closed(1, 3)) + Interval.closed(10, 12) union IntervalSet(Interval.closed(3, 10))
res8: continuum.IntervalSet[Int] = IntervalSet([1, 12])

scala> IntervalSet(Interval.closed(1, 3)) + Interval.closed(10, 12) difference IntervalSet(Interval.closed(2, 11))
res9: continuum.IntervalSet[Int] = IntervalSet([1, 2), (11, 12])

// Gaps, complement, and span
scala> val two = IntervalSet(Interval.closed(1, 3)) + Interval.closed(10, 12)
two: continuum.IntervalSet[Int] = IntervalSet([1, 3], [10, 12])

scala> two.gaps
res10: continuum.IntervalSet[Int] = IntervalSet((3, 10))

scala> two.complement
res11: continuum.IntervalSet[Int] = IntervalSet((-∞, 1), (3, 10), (12, ∞))

scala> two.spanOption
res12: Option[continuum.Interval[Int]] = Some([1, 12])
```

## Discrete domains

The `Discrete[T]` type class describes discrete domains: `next` and `prev` step between adjacent values. Instances are provided for `Int`, `Long`, and `Array[Byte]` (lexicographic, HBase-key style; pair it with the non-implicit `Discrete.ByteArrayOrdering`).

```scala
// Intervals over discrete domains may be normalized to closed-open form.
// NormalizedBound distinguishes Value, Unbounded, and Empty (no value in the domain).
scala> Interval.openClosed(10, 20).normalize
res13: (continuum.NormalizedBound[Int], continuum.NormalizedBound[Int]) = (Value(11),Value(21))

scala> Interval.greaterThan(12).normalize
res14: (continuum.NormalizedBound[Int], continuum.NormalizedBound[Int]) = (Value(13),Unbounded)

scala> Interval.greaterThan(Int.MaxValue).normalize   // contains no Ints
res15: (continuum.NormalizedBound[Int], continuum.NormalizedBound[Int]) = (Empty,Unbounded)

// Byte-array intervals (e.g. row-key ranges)
scala> implicit val ord: Ordering[Array[Byte]] = continuum.Discrete.ByteArrayOrdering

scala> val rowRange = Interval.closedOpen(Array[Byte](1), Array[Byte](2))

scala> rowRange(Array[Byte](1, 5))
res16: Boolean = true

scala> rowRange(Array[Byte](2))
res17: Boolean = false
```

## License

Copyright © 2013 Dan Burkert

Distributed under the Apache License, Version 2.0

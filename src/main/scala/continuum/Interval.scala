package continuum

import scala.language.implicitConversions

import continuum.Cut.{AboveAll, AboveValue, BelowAll, BelowValue}

/**
 * A non-empty bounded interval over a continuous, infinite, total-ordered set of values. An
 * interval contains all values between its lower and upper cut. The lower and/or upper cut may
 * be unbounded (`BelowAll`/`AboveAll`). Any operation which could potentially return an empty
 * interval returns an Option type instead.
 *
 * An interval is non-empty exactly when `lower < upper` in the total order on cuts, which is the
 * construction invariant.
 *
 * @param lower cut of the interval.
 * @param upper cut of the interval. Must be greater than `lower`.
 * @tparam T type of values contained in the continuous, infinite, total-ordered set which the
 *           interval operates on.
 */
final case class Interval[T: Ordering](lower: Cut[T], upper: Cut[T])
    extends (T => Boolean)
    with Ordered[Interval[T]] {

  require(Interval.validate(lower, upper), "Invalid interval cuts: " + lower + ", " + upper + ".")

  private[this] val cuts: Ordering[Cut[T]] = Cut.ordering

  /**
   * Tests if this interval contains the specified point.
   */
  override def apply(point: T): Boolean =
    cuts.lteq(lower, BelowValue(point)) && cuts.lteq(AboveValue(point), upper)

  /**
   * Tests if this interval intersects the other. Intervals intersect if they share any points in
   * common. Said another way, intervals intersect if they overlap.
   */
  def intersects(other: Interval[T]): Boolean =
    cuts.lt(lower, other.upper) && cuts.lt(other.lower, upper)

  /**
   * Returns the intersection of this interval and the other, or `None` if the intersection does not
   * exist.
   */
  def intersect(other: Interval[T]): Option[Interval[T]] =
    if (intersects(other)) {
      val l = cuts.max(lower, other.lower)
      val u = cuts.min(upper, other.upper)
      if ((l == lower) && (u == upper)) Some(this)
      else if ((l == other.lower) && (u == other.upper)) Some(other)
      else Some(Interval(l, u))
    } else None

  /**
   * Tests if this interval is tangent to the other. Intervals are tangent if they do not contain
   * any points in common, but the span of the intervals does not contain any points not in one of
   * the intervals.
   */
  def tangents(other: Interval[T]): Boolean =
    (lower == other.upper) || (upper == other.lower)

  /**
   * Tests if this interval unions the other. Intervals union if all the points contained by their
   * span are contained by one of the intervals. Said another way, intervals union if they
   * overlap or are tangent.
   */
  def unions(other: Interval[T]): Boolean =
    cuts.lteq(lower, other.upper) && cuts.lteq(other.lower, upper)

  /**
   * Returns the union of this interval and the other, if the intervals union.
   */
  def union(other: Interval[T]): Option[Interval[T]] =
    if (unions(other)) Some(span(other)) else None

  /**
   * Returns the minimum spanning interval of this interval and the other interval. An interval
   * spans a pair of intervals if it encloses both.
   */
  def span(other: Interval[T]): Interval[T] = {
    val l = cuts.min(lower, other.lower)
    val u = cuts.max(upper, other.upper)
    if ((l == lower) && (u == upper)) this
    else if ((l == other.lower) && (u == other.upper)) other
    else Interval(l, u)
  }

  /**
   * Tests if this interval encloses the other. An interval encloses another if it contains all
   * points contained by the other. The union of an interval with an enclosed interval is the
   * enclosing interval. The intersection of an interval with an enclosed interval is the enclosed
   * interval.
   */
  def encloses(other: Interval[T]): Boolean =
    cuts.lteq(lower, other.lower) && cuts.lteq(other.upper, upper)

  /**
   * Intervals are compared first by their lower cuts, and then by their upper cuts.
   */
  def compare(other: Interval[T]): Int = {
    val c = cuts.compare(lower, other.lower)
    if (c != 0) c
    else cuts.compare(upper, other.upper)
  }

  /**
   * Returns the difference between this interval and the other. The set may contain 0, 1, or 2
   * resulting intervals.
   */
  def difference(other: Interval[T]): IntervalSet[T] =
    intersect(other).fold(IntervalSet(this)) { intersection =>
      val left = intersection.lesser.flatMap(intersect)
      val right = intersection.greater.flatMap(intersect)
      IntervalSet((left.toList ::: right.toList): _*)
    }

  /**
   * Returns an interval which encompasses all values less than this interval, if such an interval
   * exists.
   */
  def lesser: Option[Interval[T]] =
    if (lower == BelowAll) None else Some(Interval(BelowAll, lower))

  /**
   * Returns an interval which encompasses all values greater than this interval, if such an
   * interval exists.
   */
  def greater: Option[Interval[T]] =
    if (upper == AboveAll) None else Some(Interval(upper, AboveAll))

  /**
   * Returns a normalized form of this Interval over a discrete domain: the lower bound as an
   * inclusive `Value` and the upper bound as an exclusive `Value`. A side without a bound
   * normalizes to `Unbounded`; this includes a closed upper bound at the domain's maximum, which
   * has no exclusive representation. A lower bound open at the domain's maximum normalizes to
   * `Empty`: the interval contains no values of the discrete domain.
   */
  def normalize(implicit discrete: Discrete[T]): (NormalizedBound[T], NormalizedBound[T]) = {
    val l = lower match {
      case BelowValue(cut) => NormalizedBound.Value(cut)
      case AboveValue(cut) =>
        discrete.next(cut).fold[NormalizedBound[T]](NormalizedBound.Empty)(NormalizedBound.Value(_))
      case _ => NormalizedBound.Unbounded
    }
    val u = upper match {
      case BelowValue(cut) => NormalizedBound.Value(cut)
      case AboveValue(cut) =>
        discrete
          .next(cut)
          .fold[NormalizedBound[T]](NormalizedBound.Unbounded)(NormalizedBound.Value(_))
      case _ => NormalizedBound.Unbounded
    }
    (l, u)
  }

  /**
   * Tests if this interval encloses only a single discrete point.
   */
  def isPoint: Boolean = point.isDefined

  /**
   * Returns the discrete value enclosed by this interval, if it is a point.
   */
  def point: Option[T] = (lower, upper) match {
    case (BelowValue(l), AboveValue(u)) if l == u => Some(l)
    case _                                        => None
  }

  /**
   * Transform the bounds of this interval to create a new Interval. The resulting interval must be
   * valid, i.e., the transformation must keep the relative order of the bounds.
   */
  def map[U: Ordering](f: T => U): Interval[U] =
    Interval(lower.map(f), upper.map(f))

  /**
   * Converts this interval to a [[scala.collection.immutable.Range]], if possible.
   *
   * @throws IllegalArgumentException if a bound cannot be exactly represented as an `Int`, or if
   *                                  the resulting range would contain more than
   *                                  [[scala.Int.MaxValue]] elements.
   */
  def toRange(implicit num: Numeric[T]): Range = {
    def toIntExact(value: T): Int = {
      val i = num.toInt(value)
      require(
        num.equiv(num.fromInt(i), value),
        "Bound " + value + " cannot be exactly represented as an Int."
      )
      i
    }
    val start: Int = lower match {
      case BelowValue(c) => toIntExact(c)
      case AboveValue(c) => {
        val i = toIntExact(c)
        if (i == Int.MaxValue) return Range(Int.MaxValue, Int.MaxValue)
        else i + 1
      }
      case _ => Int.MinValue
    }
    upper match {
      case AboveValue(c) => Range.inclusive(start, toIntExact(c))
      case BelowValue(c) => Range(start, toIntExact(c))
      case _             => Range.inclusive(start, Int.MaxValue)
    }
  }

  override def toString(): String = {
    def lowerString: String = lower match {
      case BelowValue(cut) => "[" + cut.toString
      case AboveValue(cut) => "(" + cut.toString
      case _               => "(-∞"
    }
    def upperString: String = upper match {
      case AboveValue(cut) => cut.toString + "]"
      case BelowValue(cut) => cut.toString + ")"
      case _               => "∞)"
    }
    point match {
      case Some(p) => "[" + p + "]"
      case None    => lowerString + ", " + upperString
    }
  }
}

object Interval {

  private[continuum] def validate[T: Ordering](lower: Cut[T], upper: Cut[T]): Boolean =
    Cut.ordering[T].lt(lower, upper)

  def open[T: Ordering](lower: T, upper: T): Interval[T] =
    Interval(AboveValue(lower), BelowValue(upper))

  def closed[T: Ordering](lower: T, upper: T): Interval[T] =
    Interval(BelowValue(lower), AboveValue(upper))

  def openClosed[T: Ordering](lower: T, upper: T): Interval[T] =
    Interval(AboveValue(lower), AboveValue(upper))

  def closedOpen[T: Ordering](lower: T, upper: T): Interval[T] =
    Interval(BelowValue(lower), BelowValue(upper))

  def greaterThan[T: Ordering](cut: T): Interval[T] =
    Interval(AboveValue(cut), AboveAll)

  def atLeast[T: Ordering](cut: T): Interval[T] =
    Interval(BelowValue(cut), AboveAll)

  def lessThan[T: Ordering](cut: T): Interval[T] =
    Interval(BelowAll, BelowValue(cut))

  def atMost[T: Ordering](cut: T): Interval[T] =
    Interval(BelowAll, AboveValue(cut))

  def full[T: Ordering]: Interval[T] =
    Interval(BelowAll, AboveAll)

  def all[T: Ordering]: Interval[T] = full

  def point[T: Ordering](point: T): Interval[T] = closed(point, point)

  def apply[T: Ordering]: Interval[T] = full

  def apply[T: Ordering](point: T): Interval[T] = closed(point, point)

  implicit def fromTuple[T: Ordering](tuple: (T, T)): Interval[T] =
    closedOpen(tuple._1, tuple._2)

  implicit def fromRange(range: Range): Interval[Int] = {
    require(range.step == 1, "Range must be continuous.")
    if (range.isInclusive) closed(range.start, range.end)
    else closedOpen(range.start, range.end)
  }

  def rightOrdering[T: Ordering]: Ordering[Interval[T]] = new Ordering[Interval[T]] {
    private val cuts = Cut.ordering[T]
    def compare(a: Interval[T], b: Interval[T]): Int = {
      val c = cuts.compare(a.upper, b.upper)
      if (c != 0) c
      else cuts.compare(a.lower, b.lower)
    }
  }
}

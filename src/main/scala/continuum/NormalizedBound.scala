package continuum

/**
 * A single side of an interval normalized to closed-open form over a discrete domain; see
 * `Interval.normalize`.
 */
sealed abstract class NormalizedBound[+T]

object NormalizedBound {

  /**
   * The interval is unbounded in this direction, or the bound has no closed-open representation
   * because it reaches the domain's edge.
   */
  case object Unbounded extends NormalizedBound[Nothing]

  /**
   * No discrete value exists in this direction: the interval contains no values of the domain.
   */
  case object Empty extends NormalizedBound[Nothing]

  /**
   * The normalized bound value: inclusive for a lower bound, exclusive for an upper bound.
   */
  final case class Value[+T](value: T) extends NormalizedBound[T]
}

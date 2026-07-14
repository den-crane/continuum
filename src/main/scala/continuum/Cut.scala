package continuum

/**
 * A cut in the space of values `T`, extended with positions just below and just above every value
 * as well as below and above all values. Cuts are totally ordered:
 *
 * {{{
 * BelowAll < BelowValue(a) < AboveValue(a) < BelowValue(b) < AboveValue(b) < AboveAll   (for a < b)
 * }}}
 *
 * An interval is the set of values lying between two cuts, which expresses every bound shape:
 * a closed lower or open upper bound at `v` is `BelowValue(v)`, an open lower or closed upper
 * bound at `v` is `AboveValue(v)`, and a missing bound is `BelowAll`/`AboveAll`. All interval
 * geometry reduces to comparisons of cuts.
 *
 * @tparam T type of values contained in the continuous, infinite, total-ordered set which the
 *           cut operates on.
 */
sealed abstract class Cut[+T] {
  def map[U](f: T => U): Cut[U]
}

object Cut {

  /** The cut below all values: the lower bound of an interval unbounded below. */
  case object BelowAll extends Cut[Nothing] {
    def map[U](f: Nothing => U): Cut[U] = this
  }

  /** The cut just below `value`: a closed lower bound or an open upper bound at `value`. */
  final case class BelowValue[T](value: T) extends Cut[T] {
    def map[U](f: T => U): Cut[U] = BelowValue(f(value))
  }

  /** The cut just above `value`: an open lower bound or a closed upper bound at `value`. */
  final case class AboveValue[T](value: T) extends Cut[T] {
    def map[U](f: T => U): Cut[U] = AboveValue(f(value))
  }

  /** The cut above all values: the upper bound of an interval unbounded above. */
  case object AboveAll extends Cut[Nothing] {
    def map[U](f: Nothing => U): Cut[U] = this
  }

  implicit def ordering[T](implicit ord: Ordering[T]): Ordering[Cut[T]] = new Ordering[Cut[T]] {
    def compare(x: Cut[T], y: Cut[T]): Int = (x, y) match {
      case (BelowAll, BelowAll)           => 0
      case (BelowAll, _)                  => -1
      case (_, BelowAll)                  => 1
      case (AboveAll, AboveAll)           => 0
      case (AboveAll, _)                  => 1
      case (_, AboveAll)                  => -1
      case (BelowValue(a), BelowValue(b)) => ord.compare(a, b)
      case (AboveValue(a), AboveValue(b)) => ord.compare(a, b)
      case (BelowValue(a), AboveValue(b)) =>
        val c = ord.compare(a, b)
        if (c == 0) -1 else c
      case (AboveValue(a), BelowValue(b)) =>
        val c = ord.compare(a, b)
        if (c == 0) 1 else c
    }
  }
}

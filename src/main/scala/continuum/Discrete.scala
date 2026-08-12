package continuum

/**
 * A trait for describing discrete domains.
 */
trait Discrete[T] {

  /**
   * Returns the next value in the domain, or `None` if the value is the domain's maximum.
   */
  def next(value: T): Option[T]

  /**
   * Returns the previous value in the domain, or `None` if no immediate predecessor exists.
   */
  def prev(value: T): Option[T]
}

object Discrete {

  /**
   * An implementation of the Discrete trait for longs.
   */
  implicit object DiscreteLong extends Discrete[Long] {
    override def next(long: Long): Option[Long] =
      if (long == Long.MaxValue) None else Some(long + 1)
    override def prev(long: Long): Option[Long] =
      if (long == Long.MinValue) None else Some(long - 1)
  }

  /**
   * An implementation of the Discrete trait for ints.
   */
  implicit object DiscreteInt extends Discrete[Int] {
    override def next(int: Int): Option[Int] = if (int == Int.MaxValue) None else Some(int + 1)
    override def prev(int: Int): Option[Int] = if (int == Int.MinValue) None else Some(int - 1)
  }

  /**
   * The discrete domain of byte arrays under unsigned lexicographic order (see
   * `ByteArrayOrdering`), as used e.g. for HBase row keys. The immediate successor of an array is
   * the array extended with a zero byte. An immediate predecessor exists only for arrays ending
   * in a zero byte (obtained by dropping it); any other array has arbitrarily close smaller
   * neighbours, so `prev` returns `None`.
   */
  implicit object DiscreteByteArray extends Discrete[Array[Byte]] {
    override def next(value: Array[Byte]): Option[Array[Byte]] = Some(value :+ 0.toByte)
    override def prev(value: Array[Byte]): Option[Array[Byte]] =
      if (value.nonEmpty && value.last == 0) Some(value.dropRight(1)) else None
  }

  /**
   * Unsigned lexicographic ordering for byte arrays, matching `DiscreteByteArray`. Not implicit;
   * bring it into scope where an `Ordering[Array[Byte]]` is required:
   *
   * {{{
   * implicit val ord: Ordering[Array[Byte]] = Discrete.ByteArrayOrdering
   * }}}
   */
  val ByteArrayOrdering: Ordering[Array[Byte]] = new Ordering[Array[Byte]] {
    def compare(a: Array[Byte], b: Array[Byte]): Int = java.util.Arrays.compareUnsigned(a, b)
  }
}

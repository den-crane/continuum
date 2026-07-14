package continuum

import scala.collection.immutable.{SortedSet, TreeSet}

object IntervalSet {

  def empty[T: Ordering]: IntervalSet[T] = new IntervalSet(TreeSet.empty[Interval[T]])

  def apply[T: Ordering](intervals: Interval[T]*): IntervalSet[T] = from(intervals)

  def from[T: Ordering](intervals: IterableOnce[Interval[T]]): IntervalSet[T] =
    intervals.iterator.foldLeft(empty[T])(_ + _)
}

/**
 * A set of 0 or more intervals. Intervals which may be unioned together are automatically
 * coalesced, so at all times an interval set contains the minimum number of necessary intervals.
 * Interval sets are immutable and persistent.
 *
 * An interval set is deliberately not a scala `Set[Interval[T]]`: its operations are geometric —
 * adding an interval coalesces it with connected members, removing an interval clips members —
 * which cannot satisfy the `Set` contract that an added element is subsequently contained. It is
 * an `Iterable` of its members in sorted order; the member set with strict element semantics is
 * available through `intervals`.
 *
 * Because members are always coalesced they are pairwise disjoint and non-tangent, so the members
 * which intersect or connect a given interval form a contiguous run in member order. Candidate
 * lookup therefore needs only the sorted-set API (`maxBefore` plus `iteratorFrom`) and runs in
 * O(log n + k).
 */
final class IntervalSet[T] private (private val tree: TreeSet[Interval[T]])(implicit
    ord: Ordering[T]
) extends (T => Boolean)
    with scala.collection.immutable.Iterable[Interval[T]]
    with Serializable {

  override def iterator: Iterator[Interval[T]] = tree.iterator
  override def size: Int = tree.size
  override def knownSize: Int = tree.size
  override def isEmpty: Boolean = tree.isEmpty
  override def head: Interval[T] = tree.head
  override def last: Interval[T] = tree.last

  /**
   * The members of this set, in sorted order, with strict element semantics.
   */
  def intervals: SortedSet[Interval[T]] = tree

  /**
   * Returns the members satisfying `p`, where `p` selects members intersecting or connecting the
   * given interval. Since members are pairwise disjoint and non-tangent, such members form a
   * contiguous run: the only candidate sorting before the interval is its immediate predecessor,
   * and candidates sorting after it form a prefix of `iteratorFrom`.
   */
  private def selectConnected(interval: Interval[T], p: Interval[T] => Boolean): List[Interval[T]] =
    tree.maxBefore(interval).filter(p).toList ::: tree.iteratorFrom(interval).takeWhile(p).toList

  /**
   * Returns this set with the given interval added, coalescing it with any connected members.
   */
  def incl(interval: Interval[T]): IntervalSet[T] = {
    val unionables = selectConnected(interval, _ unions interval)
    val union = unionables.foldLeft(interval)((a, b) => (a union b).get)
    new IntervalSet(tree -- unionables + union)
  }

  /** Alias for `incl`. */
  def +(interval: Interval[T]): IntervalSet[T] = incl(interval)

  /**
   * Returns this set with the given interval subtracted: members intersecting it are clipped, and
   * members it encloses are removed.
   */
  def excl(interval: Interval[T]): IntervalSet[T] = {
    val intersectings = selectConnected(interval, _ intersects interval)
    val differences = intersectings.flatMap(_ difference interval)
    new IntervalSet(tree -- intersectings ++ differences)
  }

  /** Alias for `excl`. */
  def -(interval: Interval[T]): IntervalSet[T] = excl(interval)

  /**
   * Tests if the given point is covered by this set.
   */
  override def apply(point: T): Boolean = containsPoint(point)

  /**
   * Tests if the given point is covered by this set.
   */
  def containsPoint(point: T): Boolean = encloses(Interval.point(point))

  /**
   * Tests if the given interval is entirely covered by this set, i.e., if one of this set's
   * intervals encloses it.
   */
  def encloses(interval: Interval[T]): Boolean = {
    val intersectings = selectConnected(interval, _ intersects interval)
    intersectings.size == 1 && intersectings.head.encloses(interval)
  }

  /**
   * Tests if the provided interval intersects with any of the intervals in this set.
   */
  def intersects(interval: Interval[T]): Boolean =
    selectConnected(interval, _ intersects interval).nonEmpty

  /**
   * Returns the subset of intervals which intersect with the given interval.
   */
  def intersecting(interval: Interval[T]): IntervalSet[T] =
    new IntervalSet(TreeSet.from(selectConnected(interval, _ intersects interval)))

  /**
   * Returns the subset of intervals which union with the given interval.
   */
  def unioning(interval: Interval[T]): IntervalSet[T] =
    new IntervalSet(TreeSet.from(selectConnected(interval, _ unions interval)))

  /**
   * Returns the result of the intervals in this set intersected with the given interval.
   */
  def intersect(interval: Interval[T]): IntervalSet[T] = {
    val clipped = selectConnected(interval, _ intersects interval).flatMap(_ intersect interval)
    new IntervalSet(TreeSet.from(clipped))
  }

  /** Alias for `intersect`. */
  def &(interval: Interval[T]): IntervalSet[T] = intersect(interval)

  /**
   * Returns the result of the intervals in this set intersected with each of the given intervals.
   */
  def intersect(that: IterableOnce[Interval[T]]): IntervalSet[T] =
    that.iterator.foldLeft(IntervalSet.empty[T])((acc, interval) =>
      acc union this.intersect(interval)
    )

  /**
   * Returns this set with all of the given intervals added.
   */
  def union(that: IterableOnce[Interval[T]]): IntervalSet[T] =
    that.iterator.foldLeft(this)(_ + _)

  /** Alias for `union`. */
  def ++(that: IterableOnce[Interval[T]]): IntervalSet[T] = union(that)

  /**
   * Returns this set with all of the given intervals subtracted.
   */
  def difference(that: IterableOnce[Interval[T]]): IntervalSet[T] =
    that.iterator.foldLeft(this)(_ - _)

  /** Alias for `difference`. */
  def --(that: IterableOnce[Interval[T]]): IntervalSet[T] = difference(that)

  /**
   * Returns the minimum spanning interval of the intervals in this set, if the set is non-empty.
   * Named `spanOption` to avoid clashing with `Iterable.span(predicate)`.
   */
  def spanOption: Option[Interval[T]] = if (tree.nonEmpty) Some(tree.head span tree.last) else None

  /**
   * Returns the bounded gaps between consecutive intervals of this set.
   */
  def gaps: IntervalSet[T] = new IntervalSet(TreeSet.from(gapIntervals))

  /**
   * Returns the interval set covering exactly the points not covered by this set: the gaps plus
   * the unbounded pieces on either side. Built in a single pass over the sorted members.
   */
  def complement: IntervalSet[T] =
    if (tree.isEmpty) IntervalSet(Interval.all[T])
    else {
      val buf = List.newBuilder[Interval[T]]
      if (tree.head.lower != Cut.BelowAll) buf += Interval(Cut.BelowAll, tree.head.lower)
      buf ++= gapIntervals
      if (tree.last.upper != Cut.AboveAll) buf += Interval(tree.last.upper, Cut.AboveAll)
      new IntervalSet(TreeSet.from(buf.result()))
    }

  /**
   * The gap between each pair of consecutive members. Valid intervals because members are
   * pairwise disjoint and non-tangent, and safe to insert directly for the same reason.
   */
  private def gapIntervals: Iterator[Interval[T]] =
    iterator.sliding(2).withPartial(false).map(pair => Interval(pair(0).upper, pair(1).lower))

  override def equals(other: Any): Boolean = other match {
    case that: IntervalSet[_] => tree == that.tree
    case _                    => false
  }

  override def hashCode(): Int = tree.hashCode()

  override def toString: String = mkString("IntervalSet(", ", ", ")")
}

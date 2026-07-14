package continuum

import scala.collection.immutable.{AbstractSet, SortedSet, SortedSetOps, StrictOptimizedSortedSetOps, TreeSet}
import scala.collection.{mutable, BuildFrom}

object IntervalSet {
  def empty[T: Ordering]: IntervalSet[T] = new IntervalSet(TreeSet.empty[Interval[T]])

  def apply[T: Ordering](intervals: Interval[T]*): IntervalSet[T] =
    intervals.foldLeft(empty[T])(_ + _)

  def newBuilder[T: Ordering]: mutable.Builder[Interval[T], IntervalSet[T]] =
    new mutable.Builder[Interval[T], IntervalSet[T]] {
      private var set = empty[T]
      def addOne(elem: Interval[T]): this.type = {
        set = set.incl(elem)
        this
      }
      def clear(): Unit = {
        set = empty[T]
      }
      def result(): IntervalSet[T] = set
    }

  implicit def buildFrom[T: Ordering]: BuildFrom[IntervalSet[_], Interval[T], IntervalSet[T]] =
    new BuildFrom[IntervalSet[_], Interval[T], IntervalSet[T]] {
      def fromSpecific(from: IntervalSet[_])(it: IterableOnce[Interval[T]]): IntervalSet[T] =
        it.iterator.foldLeft(empty[T])(_ + _)
      def newBuilder(from: IntervalSet[_]): mutable.Builder[Interval[T], IntervalSet[T]] =
        IntervalSet.newBuilder[T]
    }
}

/**
 * A set containing 0 or more intervals. Intervals which may be unioned together are automatically
 * coalesced, so at all times an interval set contains the minimum number of necessary intervals.
 * Interval sets are immutable and persistent.
 *
 * Because members are always coalesced they are pairwise disjoint and non-tangent, so the members
 * which intersect or connect a given interval form a contiguous run in member order. Candidate
 * lookup therefore needs only the sorted-set API (`maxBefore` plus `iteratorFrom`) and runs in
 * O(log n + k).
 */
class IntervalSet[T: Ordering] private (tree: TreeSet[Interval[T]])
  extends AbstractSet[Interval[T]]
  with SortedSet[Interval[T]]
  with SortedSetOps[Interval[T], SortedSet, IntervalSet[T]]
  with StrictOptimizedSortedSetOps[Interval[T], SortedSet, IntervalSet[T]]
  with Serializable {

  override def ordering: Ordering[Interval[T]] = tree.ordering

  override protected def fromSpecific(coll: IterableOnce[Interval[T]]): IntervalSet[T] =
    coll.iterator.foldLeft(IntervalSet.empty[T])(_ + _)

  override protected def newSpecificBuilder: mutable.Builder[Interval[T], IntervalSet[T]] =
    IntervalSet.newBuilder[T]

  override def className = "IntervalSet"

  override def empty: IntervalSet[T] = IntervalSet.empty

  override def size: Int = tree.size
  override def knownSize: Int = tree.size
  override def isEmpty: Boolean = tree.isEmpty
  override def head: Interval[T] = tree.head
  override def last: Interval[T] = tree.last

  override def iterator: Iterator[Interval[T]] = tree.iterator
  override def iteratorFrom(start: Interval[T]): Iterator[Interval[T]] = tree.iteratorFrom(start)
  override def foreach[U](f: Interval[T] => U): Unit = tree.foreach(f)

  override def rangeImpl(from: Option[Interval[T]], until: Option[Interval[T]]): IntervalSet[T] =
    new IntervalSet(tree.rangeImpl(from, until))

  override def drop(n: Int): IntervalSet[T] = new IntervalSet(tree.drop(n))
  override def take(n: Int): IntervalSet[T] = new IntervalSet(tree.take(n))
  override def slice(from: Int, until: Int): IntervalSet[T] = new IntervalSet(tree.slice(from, until))

  /**
   * Returns the members satisfying `p`, where `p` selects members intersecting or connecting the
   * given interval. Since members are pairwise disjoint and non-tangent, such members form a
   * contiguous run: the only candidate sorting before the interval is its immediate predecessor,
   * and candidates sorting after it form a prefix of `iteratorFrom`.
   */
  private def selectConnected(interval: Interval[T], p: Interval[T] => Boolean): List[Interval[T]] =
    tree.maxBefore(interval).filter(p).toList ::: tree.iteratorFrom(interval).takeWhile(p).toList

  def incl(interval: Interval[T]): IntervalSet[T] = {
    val unionables = selectConnected(interval, _ unions interval)
    val union = unionables.foldLeft(interval)((a, b) => (a union b).get)
    new IntervalSet(tree -- unionables + union)
  }

  def excl(interval: Interval[T]): IntervalSet[T] = {
    val intersectings = selectConnected(interval, _ intersects interval)
    val differences = intersectings.flatMap(_ difference interval)
    new IntervalSet(tree -- intersectings ++ differences)
  }

  /**
   * Tests if the given interval is an element of this set. Note that intervals added to the set
   * are coalesced with connected intervals, so an added interval is not necessarily an element
   * afterwards. Use `encloses` to test whether this set covers an interval.
   */
  override def contains(interval: Interval[T]): Boolean = tree.contains(interval)

  /**
   * Tests if the given interval is entirely covered by this set, i.e., if one of this set's
   * intervals encloses it.
   */
  def encloses(interval: Interval[T]): Boolean = {
    val intersectings = selectConnected(interval, _ intersects interval)
    intersectings.size == 1 && intersectings.head.encloses(interval)
  }

  def containsPoint(point: T): Boolean = encloses(Interval.point(point))

  /**
   * Returns the subset of intervals which intersect with the given interval.
   */
  def intersecting(interval: Interval[T]): IntervalSet[T] =
    new IntervalSet(TreeSet.from(selectConnected(interval, _ intersects interval))(ordering))

  /**
   * Tests if the provided interval intersects with any of the intervals in this set.
   */
  def intersects(interval: Interval[T]): Boolean =
    selectConnected(interval, _ intersects interval).nonEmpty

  /**
   * Returns the the result of the intervals in this set intersected with the given interval.
   */
  def intersect(interval: Interval[T]): IntervalSet[T] = {
    val clipped = selectConnected(interval, _ intersects interval).flatMap(_ intersect interval)
    new IntervalSet(TreeSet.from(clipped)(ordering))
  }

  /**
   * Returns the result of the intervals in this set intersected with each of the given intervals
   * (a geometric intersection). Unlike the element-wise `intersect(that: collection.Set[...])`
   * inherited from `Set`, member intervals are clipped against the given intervals, not matched
   * for equality.
   */
  def intersectAll(that: Set[Interval[T]]): IntervalSet[T] =
    that.foldLeft(IntervalSet.empty[T])((acc, interval) => acc ++ this.intersect(interval))

  /**
   * Alias for `intersect`.
   */
  def &(interval: Interval[T]): IntervalSet[T] = intersect(interval)

  /**
   * Returns the subset of intervals which union with the given interval.
   */
  def unioning(interval: Interval[T]): IntervalSet[T] =
    new IntervalSet(TreeSet.from(selectConnected(interval, _ unions interval))(ordering))

  def span: Option[Interval[T]] = if (tree.nonEmpty) Some(tree.head span tree.last) else None

  def complement: IntervalSet[T] = {
    val full = IntervalSet(Interval.all[T])
    this.foldLeft(full)((acc, interval) => acc - interval)
  }
}

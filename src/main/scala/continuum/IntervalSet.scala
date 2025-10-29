package scala.collection.immutable

import scala.collection.immutable.{RedBlackTree => RB}
import scala.collection.mutable

import continuum.Interval

object IntervalSet {
  def empty[T](implicit conv: T=>Ordered[T]): IntervalSet[T] = new IntervalSet()

  def apply[T](intervals: Interval[T]*)(implicit conv: T=>Ordered[T]): IntervalSet[T] =
    intervals.foldLeft(empty[T])(_ + _)

  def newBuilder[T](implicit conv: T=>Ordered[T]): mutable.Builder[Interval[T], IntervalSet[T]] =
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

  implicit def buildFrom[T](implicit conv: T=>Ordered[T]): collection.BuildFrom[IntervalSet[_], Interval[T], IntervalSet[T]] =
    new collection.BuildFrom[IntervalSet[_], Interval[T], IntervalSet[T]] {
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
 */
class IntervalSet[T](tree: RB.Tree[Interval[T], Unit])(implicit conv: T=>Ordered[T])
  extends AbstractSet[Interval[T]]
  with SortedSet[Interval[T]]
  with SortedSetOps[Interval[T], SortedSet, IntervalSet[T]]
  with StrictOptimizedSortedSetOps[Interval[T], SortedSet, IntervalSet[T]]
  with Serializable {

  def this()(implicit conv: T=>Ordered[T]) = this(null)

  override def ordering: Ordering[Interval[T]] = Ordering.ordered

  override protected def fromSpecific(coll: IterableOnce[Interval[T]]): IntervalSet[T] =
    coll.iterator.foldLeft(IntervalSet.empty[T])(_ + _)

  override protected def newSpecificBuilder: mutable.Builder[Interval[T], IntervalSet[T]] =
    IntervalSet.newBuilder[T]

  override def stringPrefix = "IntervalSet"

  override def size = RB.count(tree)

  override def head = RB.smallest(tree).key
  override def headOption = if (RB.isEmpty(tree)) None else Some(head)
  override def last = RB.greatest(tree).key
  override def lastOption = if (RB.isEmpty(tree)) None else Some(last)

  override def tail = new IntervalSet(RB.delete(tree, firstKey))
  override def init = new IntervalSet(RB.delete(tree, lastKey))

  override def drop(n: Int) = {
    if (n <= 0) this
    else if (n >= size) empty
    else newSet(RB.drop(tree, n))
  }

  override def take(n: Int) = {
    if (n <= 0) empty
    else if (n >= size) this
    else newSet(RB.take(tree, n))
  }

  override def slice(from: Int, until: Int) = {
    if (until <= from) empty
    else if (from <= 0) take(until)
    else if (until >= size) drop(from)
    else newSet(RB.slice(tree, from, until))
  }

  override def dropRight(n: Int) = take(size - n)
  override def takeRight(n: Int) = drop(size - n)
  override def splitAt(n: Int) = (take(n), drop(n))

  private[this] def countWhile(p: Interval[T] => Boolean): Int = {
    var result = 0
    val it = iterator
    while (it.hasNext && p(it.next())) result += 1
    result
  }
  override def dropWhile(p: Interval[T] => Boolean) = drop(countWhile(p))
  override def takeWhile(p: Interval[T] => Boolean) = take(countWhile(p))
  override def span(p: Interval[T] => Boolean) = splitAt(countWhile(p))

  private def newSet(t: RB.Tree[Interval[T], Unit]) = new IntervalSet(t)

  override def empty: IntervalSet[T] = IntervalSet.empty

  def incl(interval: Interval[T]): IntervalSet[T] = {
    val unionables: IntervalSet[T] = unioning(interval)
    val union = unionables.foldLeft(interval)((a, b) => (a union b).get)
    val diff = unionables.foldLeft(tree)(RB.delete(_, _))
    newSet(RB.update(diff, union, (), false))
  }

  def excl(interval: Interval[T]): IntervalSet[T] = {
    val intersectings = intersecting(interval)
    val differences = intersectings.flatMap(_ difference interval)
    val diff = intersectings.foldLeft(tree)(RB.delete(_, _))
    newSet(differences.foldLeft(diff)(RB.update(_, _, (), false)))
  }

  override def contains(interval: Interval[T]): Boolean = {
    val intersectings = intersecting(interval)
    intersectings.size == 1 && intersectings.head.encloses(interval)
  }

  def containsPoint(point: T): Boolean = contains(Interval.point(point))

  override def iterator: Iterator[Interval[T]] = RB.keysIterator(tree)

  override def iteratorFrom(start: Interval[T]): Iterator[Interval[T]] = {
    RB.keysIterator(RB.from(tree, start))
  }

  override def foreach[U](f: Interval[T] =>  U) = RB.foreachKey(tree, f)

  override def rangeImpl(from: Option[Interval[T]], until: Option[Interval[T]]): IntervalSet[T] = newSet(RB.rangeImpl(tree, from, until))
  override def range(from: Interval[T], until: Interval[T]): IntervalSet[T] = newSet(RB.range(tree, from, until))

  override def firstKey = head
  override def lastKey = last

  /**
   * Returns the subset of intervals which intersect with the given interval.
   */
  def intersecting(interval: Interval[T]): IntervalSet[T] = {
    val buf = mutable.ArrayBuffer[Interval[T]]()
    def loop(t: RB.Tree[Interval[T], Unit]): Unit = {
      if (!RB.isEmpty(t)) {
        if (t.key intersects interval) buf += t.key
        if (!RB.isEmpty(t.left) && (RB.greatest(t.left).key.upper intersects interval.lower))
          loop(t.left)
        if (!RB.isEmpty(t.right) && (RB.smallest(t.right).key.lower intersects interval.upper))
           loop(t.right)
      }
    }
    loop(tree)
    IntervalSet(buf.toArray:_*)
  }

  /**
   * Tests if the provided interval intersects with any of the intervals in this set.
   */
  def intersects(interval: Interval[T]): Boolean = from(interval).head intersects interval

  /**
   * Returns the the result of the intervals in this set intersected with the given interval.
   */
  def intersect(interval: Interval[T]): IntervalSet[T] =
    IntervalSet(intersecting(interval).toList.flatMap(_ intersect interval):_*)


  def intersect(that: Set[Interval[T]]): IntervalSet[T] =
    that.foldLeft(IntervalSet.empty[T])((acc, interval) => acc ++ this.intersect(interval))

  /**
   * Alias for `intersect`.
   */
  def &(interval: Interval[T]): IntervalSet[T] = intersect(interval)

  /**
   * Returns the subset of intervals which union with the given interval.
   */
  def unioning(interval: Interval[T]): IntervalSet[T] = {
    val buf = mutable.ArrayBuffer[Interval[T]]()
    def loop(tree: RB.Tree[Interval[T], Unit]): Unit = {
      if(!RB.isEmpty(tree)) {
        if (tree.key unions interval) buf += tree.key
        if (!RB.isEmpty(tree.left) && (RB.greatest(tree.left).key.upper connects interval.lower))
          loop(tree.left)
        if (!RB.isEmpty(tree.right) && (RB.smallest(tree.right).key.lower connects interval.upper))
          loop(tree.right)
      }
    }
    loop(tree)
    IntervalSet(buf.toArray:_*)
  }

  def span: Option[Interval[T]] = if (!RB.isEmpty(tree)) Some(head span last) else None

  def complement: IntervalSet[T] = {
    val full = IntervalSet(Interval.all[T])
    this.foldLeft(full)((acc, interval) => acc - interval)
  }
}

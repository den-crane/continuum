# Changelog

## 0.5.0 — 2026-07-14

First versioned release of this fork, replacing the `0.4-SNAPSHOT` carried since 2013. This is a
**breaking release**: the internal model was rewritten (Guava-style cuts), `IntervalSet` is no
longer a Scala `Set`, and the ordering abstraction moved from `Ordered` view bounds to `Ordering`
context bounds. A migration table is at the bottom.

### Fixed

- `IntervalSet.intersects` threw `NoSuchElementException` instead of returning a result whenever
  the intersecting member sorted before the query (i.e. almost always), and on empty sets.
- `Interval.toRange` silently truncated bounds through `Numeric.toInt`, mangling `Interval[Long]`
  values outside Int range and fractional Doubles. It now throws `IllegalArgumentException` when a
  bound is not exactly representable as an `Int`.
- `Interval.normalize` could not distinguish an interval that is empty in the discrete domain
  (e.g. `(Int.MaxValue, ∞)`) from one that is unbounded — both normalized to `None`. The new
  `NormalizedBound` result makes them distinct (`Empty` vs `Unbounded`).
- `.gitignore` ignored the entire `project/` directory, so `project/build.properties` (the sbt
  version pin) was never tracked.
- `Interval.compare` scaladoc claimed upper rays were compared first; lower cuts are.

### Changed

- **Ordering abstraction**: every signature now takes an `Ordering[T]` context bound instead of an
  `implicit T => Ordered[T]` view bound. Intervals work with any type that has an `Ordering`, and
  comparisons no longer allocate `Ordered` wrappers.
- **Internal model**: `Bound` (Closed/Open/Unbounded) and the mirrored `GreaterRay`/`LesserRay`
  classes are replaced by a single totally-ordered `Cut[T]` type
  (`BelowAll < BelowValue(a) < AboveValue(a) < ... < AboveAll`). Every interval operation is now a
  one-line cut comparison. `Interval` is constructed from and destructures into two cuts.
- **`IntervalSet` is no longer a `scala.Set`**: its operations are geometric (adding coalesces,
  removing clips), which cannot satisfy the `Set` laws. It is now an
  `scala.collection.immutable.Iterable` of its members in sorted order plus `T => Boolean`
  (`set(point)` tests coverage). Strict element semantics are available through the
  `intervals: SortedSet[Interval[T]]` view. Equality with plain `Set`s no longer holds.
- **`IntervalSet` internals**: moved from the `scala.collection.immutable` package (where it
  reached into the private `RedBlackTree` API) into `continuum`, wrapping a plain `TreeSet`.
  Candidate lookup uses the coalescing invariant (members are pairwise disjoint and non-tangent)
  via `maxBefore`/`iteratorFrom` at the same O(log n + k). The user-facing name
  `continuum.IntervalSet` is unchanged.
- `Interval.difference` returns `IntervalSet[T]` instead of `Set[Interval[T]]`.
- `IntervalSet.complement` is built in a single pass over the sorted members instead of repeated
  geometric subtraction.
- `Discrete[Array[T]]` (which appended a default-initialized element for any `T` and had no usable
  ordering) is replaced by `Discrete[Array[Byte]]`: the successor appends `0x00` (immediate
  lexicographic successor), the predecessor exists only for arrays ending in a zero byte.
- ScalaCheck generators (`continuum.test.Generators`) moved to the test sources and are no longer
  published; ScalaCheck is no longer a `provided` dependency of the main artifact.
- Scala 2.13.18; test dependencies: ScalaTest 3.2.20, ScalaCheck 1.19.0.

### Added

- **Scala 3 cross-build**: the same sources build for Scala 2.13.18 and Scala 3.3 LTS
  (`sbt +test`); artifacts publish as `continuum_2.13` and `continuum_3`.
- `Cut[T]` as public API, with `Cut.ordering[T]` deriving `Ordering[Cut[T]]`.
- `IntervalSet.encloses(interval)` — coverage query (the old `contains` semantics, explicitly named).
- `IntervalSet.intervals` — the member set as a `SortedSet`, with strict element semantics.
- `IntervalSet.gaps` — the bounded space between consecutive members.
- `IntervalSet.union`/`++`, `difference`/`--`, and `intersect` over any `IterableOnce[Interval[T]]`;
  `IntervalSet.from(iterable)`.
- `NormalizedBound` ADT (`Value` / `Unbounded` / `Empty`) as the result of `Interval.normalize`.
- `Discrete.prev` (with `Int`/`Long`/`Array[Byte]` implementations) and the non-implicit
  `Discrete.ByteArrayOrdering` (unsigned lexicographic).
- scalafmt (`sbt scalafmtAll`; CI check: `scalafmtCheckAll`), `versionScheme := early-semver`.

### Removed

- `continuum.bound` package, `Bound`, `Closed`, `Open`, `Unbounded`, `GreaterRay`, `LesserRay`.
- `IntervalSet.contains(interval)`, the `Set`/`SortedSet` API surface, the public no-arg
  constructor, and the interim `intersectAll` name.

### Migration guide

| 0.4 | 0.5.0 |
|---|---|
| `implicit T => Ordered[T]` requirement | `Ordering[T]` context bound |
| `Interval(GreaterRay(Closed(a)), LesserRay(Open(b)))` | `Interval(Cut.BelowValue(a), Cut.BelowValue(b))` (or `Interval.closedOpen(a, b)`) |
| `GreaterRay`/`LesserRay`/`Bound` pattern matches | match on `Cut.BelowValue`/`Cut.AboveValue`/`Cut.BelowAll`/`Cut.AboveAll` |
| `set.contains(interval)` (coverage) | `set.encloses(interval)` |
| `set.contains(interval)` (membership) | `set.intervals.contains(interval)` |
| `set(interval)` | `set.encloses(interval)`; `set(point)` now tests point coverage |
| `set.intersect(otherSet)` (element-wise, inherited) | `set.intervals intersect other.intervals` |
| `set.intersect(otherSet)` (geometric intent) | `set.intersect(otherSet)` (now actually geometric) |
| `set.span` | `set.spanOption` |
| `new IntervalSet[T]()` | `IntervalSet.empty[T]` |
| `interval.difference(other): Set[Interval[T]]` | returns `IntervalSet[T]` |
| `interval.normalize: (Option[T], Option[T])` | `(NormalizedBound[T], NormalizedBound[T])` |
| `Discrete[Array[T]]` | `Discrete[Array[Byte]]` + `implicit val ord = Discrete.ByteArrayOrdering` |
| `continuum.test.Generators` from the published jar | copy from this repo's test sources (no longer published) |

## 0.4-SNAPSHOT

The upstream state of [danburkert/continuum](https://github.com/danburkert/continuum) plus this
fork's migration from Scala 2.11.8 to 2.13.

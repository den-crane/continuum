# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Continuum is a Scala library for working with intervals over continuous, total-ordered domains. It provides functionality similar to Guava's Range library, with two primary abstractions:

- **Interval**: A non-empty, two-sided bound over a continuous, infinite, total-ordered set of values
- **IntervalSet**: A set of 0 or more intervals that automatically coalesces connected intervals

## Build Commands

```bash
# Compile the project
sbt compile

# Run tests (current Scala version)
sbt test

# Run tests for all cross-build Scala versions (2.13 and 3.3)
sbt +test

# Run specific test
sbt "testOnly continuum.IntervalSpec"

# Launch Scala REPL with project on classpath
sbt console

# Package as JAR
sbt package

# Format sources (scalafmt; CI-style check: scalafmtCheckAll)
sbt scalafmtAll scalafmtSbt
```

## Core Architecture

### Type System Hierarchy

The library is built on a hierarchy of abstractions from low-level to high-level:

1. **Cut** (`Cut.scala`) - Foundation level
   - A totally-ordered cut point in the value space:
     `BelowAll < BelowValue(a) < AboveValue(a) < BelowValue(b) < AboveValue(b) < AboveAll` (for a < b)
   - Every bound shape is a choice of cut: closed lower / open upper at `v` is `BelowValue(v)`,
     open lower / closed upper at `v` is `AboveValue(v)`, missing bounds are `BelowAll`/`AboveAll`
   - `Cut.ordering[T]` derives `Ordering[Cut[T]]` from `Ordering[T]`

2. **Interval** (`Interval.scala`) - Range level
   - Composed of a lower and an upper `Cut`; non-empty iff `lower < upper` (the construction invariant)
   - Every operation is a cut comparison: intersects is `lower < other.upper && other.lower < upper`,
     unions is the same with `<=`, encloses is `lower <= other.lower && other.upper <= upper`
   - Supports set operations: intersect, union, span, difference
   - Can be created from tuples and Ranges via implicit conversions
   - Cannot be empty (returns `Option` for operations that might yield empty intervals)

3. **IntervalSet** (`IntervalSet.scala`) - Set level
   - Immutable, persistent set of intervals
   - Automatically coalesces overlapping/tangent intervals
   - Deliberately not a Scala `Set` (geometric add/remove cannot satisfy Set laws); it is an
     immutable `Iterable` of sorted members, with the member `SortedSet` exposed via `intervals`
   - Backed by an immutable `TreeSet` for efficient operations

### Discrete Domain Support

The `Discrete[T]` trait (`Discrete.scala`) enables operations on discrete domains:

- Provides `next()`/`prev()` functions to step through the domain
- Used by `Interval.normalize()` to convert open/closed bounds
- Implementations provided for `Int`, `Long`, and `Array[Byte]` (unsigned lexicographic; matching non-implicit `Discrete.ByteArrayOrdering` supplies the `Ordering`)
- `Interval.normalize` returns a pair of `NormalizedBound` (`Value`/`Unbounded`/`Empty`), distinguishing empty-in-domain from unbounded at the domain edges

### Testing

Tests use ScalaTest with property-based testing (ScalaCheck):
- Located in `src/test/scala/continuum/`
- `Generators.scala` provides ScalaCheck generators for all types
- Tests verify algebraic properties (commutativity, associativity, idempotence)

## Key Implementation Details

- All types require an implicit `Ordering[T]` for comparison (context bound style)
- `IntervalSet` lives in the `continuum` package and wraps an immutable `TreeSet`; the coalescing invariant (members are pairwise disjoint and non-tangent) makes candidate lookup a contiguous run reachable via `maxBefore`/`iteratorFrom`
- The validation logic ensures intervals are never empty at construction time
- The total order on cuts is the foundation for all interval operations

## Scala Version

Project cross-builds for Scala 2.13.18 and Scala 3.3.7 LTS (migrated from 2.11.8). The same
sources compile for both versions with no version-specific source directories. Use `sbt +test`
to test both, `sbt "++3.3.7" test` to target Scala 3 only.

### Scala 2.13 Collections API

The library has been updated to use the Scala 2.13 collections architecture:

- `IntervalSet` extends `scala.collection.immutable.Iterable` (not `Set`); geometric operations
  are `incl`/`excl` (`+`/`-`), `union` (`++`), `difference` (`--`), `intersect`, `gaps`,
  `complement`, and `spanOption` (named to avoid clashing with `Iterable.span(predicate)`)
- Element-wise operations go through the `intervals` view (`SortedSet[Interval[T]]`)
- Tests use `AnyPropSpec` and `ScalaCheckPropertyChecks` from ScalaTest 3.2+

### Dependencies

- ScalaTest 3.2.20
- ScalaCheck 1.19.0
- ScalaTestPlus ScalaCheck integration 3.2.20.0

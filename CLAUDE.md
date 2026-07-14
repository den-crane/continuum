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

# Run tests
sbt test

# Run specific test
sbt "testOnly continuum.IntervalSpec"

# Launch Scala REPL with project on classpath
sbt console

# Package as JAR
sbt package
```

## Core Architecture

### Type System Hierarchy

The library is built on a hierarchy of abstractions from low-level to high-level:

1. **Bound** (`Bound.scala`) - Foundation level
   - Represents a single bound (Closed, Open, or Unbounded)
   - Located in `continuum.bound` package
   - Internal implementation detail for Ray

2. **Ray** (`Ray.scala`) - Half-space level
   - A bounded subset with a direction (Lesser or Greater)
   - `GreaterRay[T]`: bounded below, points towards larger values
   - `LesserRay[T]`: bounded above, points towards smaller values
   - Used to compose Interval bounds

3. **Interval** (`Interval.scala`) - Range level
   - Composed of a `GreaterRay` (lower) and `LesserRay` (upper)
   - Supports set operations: intersect, union, span, difference
   - Can be created from tuples and Ranges via implicit conversions
   - Cannot be empty (returns `Option` for operations that might yield empty intervals)

4. **IntervalSet** (`IntervalSet.scala`) - Set level
   - Immutable, persistent set of intervals
   - Automatically coalesces overlapping/tangent intervals
   - Implements full Scala `SortedSet` API
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
- Ray intersection/tangent logic is the foundation for interval operations

## Scala Version

Project uses Scala 2.13.18 (migrated from 2.11.8).

### Scala 2.13 Collections API

The library has been updated to use the Scala 2.13 collections architecture:

- `IntervalSet` extends `AbstractSet`, `SortedSet`, `SortedSetOps`, and `StrictOptimizedSortedSetOps`
- Uses `incl`/`excl` methods instead of `+`/`-` for adding/removing elements
- Implements `fromSpecific`, `newSpecificBuilder`, and `iteratorFrom` for the new collections framework
- Builder pattern uses `addOne`, `clear`, and `result` methods
- Tests updated to use `AnyPropSpec` and `ScalaCheckPropertyChecks` from ScalaTest 3.2+

### Dependencies

- ScalaTest 3.2.20
- ScalaCheck 1.19.0
- ScalaTestPlus ScalaCheck integration 3.2.20.0

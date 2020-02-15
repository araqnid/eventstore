package org.araqnid.eventstore

import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.describe

fun <T> containsOnly(matcher: Matcher<T>): Matcher<Collection<T>> =
        object : Matcher.Primitive<Collection<T>>() {
            override fun invoke(actual: Collection<T>): MatchResult {
                if (actual.isEmpty())
                    return MatchResult.Mismatch("was empty")
                if (actual.size > 1)
                    return MatchResult.Mismatch("contained multiple elements: ${describe(actual)}")
                return matcher(actual.first())
            }

            override val description: String
                get() = "in which the only element ${describe(matcher)}"
        }

fun <T> containsInOrder(vararg matchers: Matcher<T>): Matcher<Collection<T>> =
        object : Matcher.Primitive<Collection<T>>() {
            override fun invoke(actual: Collection<T>): MatchResult {
                val expectedIter = matchers.iterator()
                val actualIter = actual.iterator()
                var index = 0
                while (expectedIter.hasNext() && actualIter.hasNext()) {
                    val expectedMatcher = expectedIter.next()
                    val actualValue = actualIter.next()
                    val result = expectedMatcher(actualValue)
                    if (result is MatchResult.Mismatch) {
                        return MatchResult.Mismatch("at index $index: ${describe(result)}")
                    }
                    ++index
                }
                if (expectedIter.hasNext())
                    return MatchResult.Mismatch("expected more than $index values")
                if (actualIter.hasNext())
                    return MatchResult.Mismatch("had more than $index values: ${describe(actualIter.asSequence().toList())}")
                return MatchResult.Match
            }

            override val description: String
                get() = "contains in order: ${describe(matchers.toList())}"
        }

fun <T> containsInAnyOrder(vararg matchers: Matcher<T>): Matcher<Collection<T>> =
        object : Matcher.Primitive<Collection<T>>() {
            override fun invoke(actual: Collection<T>): MatchResult {
                val remaining = matchers.toMutableList()
                for ((actualIndex, actualValue) in actual.withIndex()) {
                    val matched = matchers.filter { matcher -> matcher(actualValue) == MatchResult.Match }
                    if (matched.isEmpty())
                        return MatchResult.Mismatch("element at $actualIndex did not satisfy any matcher: ${describe(
                                actualValue)}")
                    else if (matched.size > 1)
                        return MatchResult.Mismatch("element at $actualIndex matched multiple matchers: ${describe(
                                actualValue)}")
                    remaining.remove(matched.first())
                }
                if (remaining.isNotEmpty()) {
                    val remainingString = remaining.joinToString("; ") { describe(it) }
                    return MatchResult.Mismatch("expected at least ${matchers.size} elements: these did not match: $remainingString")
                }
                return MatchResult.Match
            }

            override val description: String
                get() = "contains in any order: ${describe(matchers.toList())}"
        }

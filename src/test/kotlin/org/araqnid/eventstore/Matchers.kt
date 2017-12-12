package org.araqnid.eventstore

import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.anything
import com.natpryce.hamkrest.describe
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.hasElement

fun <T> containsOnly(matcher: Matcher<T>): Matcher<Collection<T>> {
    return object : Matcher.Primitive<Collection<T>>() {
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
}

fun <T> containsInOrder(vararg matchers: Matcher<T>): Matcher<Collection<T>> {
    return object : Matcher.Primitive<Collection<T>>() {
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
            get() = "exactly in order: ${describe(matchers.toList())}"
    }
}

fun <T> containsInAnyOrder(vararg values: T): Matcher<Collection<T>> {
    return values.fold(anything as Matcher<Collection<T>>) { acc, v -> acc and hasElement(v) }
}

fun <K, V> hasEntry(key: K, value: V): Matcher<Map<K, V>> {
    return has("entry $key -> $value", { it[key] }, equalTo(value))
}

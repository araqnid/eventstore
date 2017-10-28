package org.araqnid.eventstore.filesystem.flatpack

import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.hamcrest.TypeSafeDiagnosingMatcher

internal fun <T> containsElement(matcher: Matcher<T>): Matcher<Collection<T>> {
    return object : TypeSafeDiagnosingMatcher<Collection<T>>() {
        override fun matchesSafely(item: Collection<T>, mismatchDescription: Description): Boolean {
            item.withIndex().forEach { (index, value) ->
                if (matcher.matches(value)) {
                    return true
                }
                if (index > 0) {
                    mismatchDescription.appendText(", ")
                }
                mismatchDescription.appendText("$index: ")
                matcher.describeMismatch(value, mismatchDescription)
            }
            return false
        }

        override fun describeTo(description: Description) {
            description.appendText("collection containing ").appendDescriptionOf(matcher)
        }
    }
}

internal fun <T> containsElement(elt: T) = containsElement(Matchers.equalTo(elt))

package org.araqnid.eventstore.testutil;

import java.util.Optional;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import static org.hamcrest.Matchers.equalTo;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class JOptionalMatchers {
	public static Matcher<Optional<?>> isEmpty() {
		return new TypeSafeDiagnosingMatcher<Optional<?>>() {
			@Override
			protected boolean matchesSafely(Optional<?> item, Description mismatchDescription) {
				if (item.isPresent()) {
					mismatchDescription.appendText("value was present: ").appendValue(item.get());
					return false;
				}
				return true;
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("no value");
			}
		};
	}

	public static Matcher<Optional<?>> isPresent(final Matcher<?> valueMatcher) {
		return new TypeSafeDiagnosingMatcher<Optional<?>>() {
			@Override
			protected boolean matchesSafely(Optional<?> item, Description mismatchDescription) {
				if (!item.isPresent()) {
					mismatchDescription.appendText("value was absent");
					return false;
				}
				if (!valueMatcher.matches(item.get())) {
					valueMatcher.describeMismatch(item.get(), mismatchDescription);
					return false;
				}
				return true;
			}

			@Override
			public void describeTo(Description description) {
				description.appendDescriptionOf(valueMatcher);
			}
		};
	}

	public static Matcher<Optional<?>> isValue(Object value) {
		return isPresent(equalTo(value));
	}

	private JOptionalMatchers() {
	}
}

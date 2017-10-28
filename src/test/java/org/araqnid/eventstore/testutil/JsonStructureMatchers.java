package org.araqnid.eventstore.testutil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.hamcrest.collection.IsIterableContainingInAnyOrder;
import org.hamcrest.collection.IsIterableContainingInOrder;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;

public final class JsonStructureMatchers {
	private static final ObjectMapper MAPPER = new ObjectMapper();

	@SuppressWarnings("unchecked")
	public static Matcher<JsonNode> jacksonTree(Matcher<? extends JsonNode> matcher) {
		return (Matcher<JsonNode>) matcher;
	}

	public static Matcher<String> json(Matcher<? extends JsonNode> matcher) {
		return new TypeSafeDiagnosingMatcher<String>() {
			@Override
			protected boolean matchesSafely(String item, Description mismatchDescription) {
				JsonNode node;
				try {
					node = MAPPER.readTree(item);
				} catch (IOException e) {
					mismatchDescription.appendText("Invalid JSON: ").appendValue(e);
					return false;
				}
				matcher.describeMismatch(node, mismatchDescription);
				return matcher.matches(node);
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("JSON ").appendDescriptionOf(matcher);
			}
		};
	}

	public static Matcher<byte[]> jsonBytes(Matcher<? extends JsonNode> matcher) {
		return new TypeSafeDiagnosingMatcher<byte[]>() {
			@Override
			protected boolean matchesSafely(byte[] item, Description mismatchDescription) {
				JsonNode node;
				try {
					node = MAPPER.readTree(item);
				} catch (IOException e) {
					mismatchDescription.appendText("Invalid JSON: ").appendValue(e);
					return false;
				}
				matcher.describeMismatch(node, mismatchDescription);
				mismatchDescription.appendText("; original JSON: ").appendText(new String(item, UTF_8));
				return matcher.matches(node);
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("JSON ").appendDescriptionOf(matcher);
			}
		};
	}

	public static ObjectNodeMatcher jsonObject() {
		return new ObjectNodeMatcher();
	}

	public static class ObjectNodeMatcher extends TypeSafeDiagnosingMatcher<ObjectNode> {
		private final Map<String, Matcher<? extends JsonNode>> propertyMatchers = new LinkedHashMap<>();
		private boolean failOnUnexpectedProperties = true;

		@Override
		protected boolean matchesSafely(ObjectNode item, Description mismatchDescription) {
			Set<String> remainingFieldNames = Sets.newHashSet(item.fieldNames());
			for (Map.Entry<String, Matcher<? extends JsonNode>> e : propertyMatchers.entrySet()) {
				if (!item.has(e.getKey())) {
					mismatchDescription.appendText(e.getKey()).appendText(" was not present");
					return false;
				}
				JsonNode value = item.get(e.getKey());
				if (!e.getValue().matches(value)) {
					mismatchDescription.appendText(e.getKey()).appendText(": ");
					e.getValue().describeMismatch(value, mismatchDescription);
					return false;
				}
				remainingFieldNames.remove(e.getKey());
			}
			if (failOnUnexpectedProperties && !remainingFieldNames.isEmpty()) {
				mismatchDescription.appendText("unexpected properties: ").appendValue(remainingFieldNames);
				return false;
			}
			return true;
		}

		@Override
		public void describeTo(Description description) {
			description.appendText("{ ");
			boolean first = true;
			for (Map.Entry<String, Matcher<? extends JsonNode>> e : propertyMatchers.entrySet()) {
				if (first) {
					first = false;
				}
				else {
					description.appendText(", ");
				}
				description.appendValue(e.getKey()).appendText(": ").appendDescriptionOf(e.getValue());
			}
			description.appendText(" }");
		}

		public ObjectNodeMatcher withProperty(String key, Matcher<? extends JsonNode> value) {
			Preconditions.checkNotNull(key, "property key must not be null");
			propertyMatchers.put(key, value);
			return this;
		}

		public ObjectNodeMatcher withProperty(String key, int value) {
			Preconditions.checkNotNull(key, "property key must not be null");
			propertyMatchers.put(key, jsonInt(value));
			return this;
		}

		public ObjectNodeMatcher withProperty(String key, long value) {
			Preconditions.checkNotNull(key, "property key must not be null");
			propertyMatchers.put(key, jsonLong(value));
			return this;
		}

		public ObjectNodeMatcher withProperty(String key, double value) {
			Preconditions.checkNotNull(key, "property key must not be null");
			propertyMatchers.put(key, jsonDouble(value));
			return this;
		}

		public ObjectNodeMatcher withProperty(String key, boolean value) {
			Preconditions.checkNotNull(key, "property key must not be null");
			propertyMatchers.put(key, jsonBoolean(value));
			return this;
		}

		public ObjectNodeMatcher withProperty(String key, String value) {
			Preconditions.checkNotNull(key, "property key must not be null");
			propertyMatchers.put(key, jsonString(value));
			return this;
		}

		public ObjectNodeMatcher withPropertyLike(String key, String apparentValue) {
			Preconditions.checkNotNull(key, "property key must not be null");
			propertyMatchers.put(key, jsonNodeStringifyingAs(apparentValue));
			return this;
		}

		public ObjectNodeMatcher withPropertyJSON(String key, String json) {
			Preconditions.checkNotNull(key, "property key must not be null");
			propertyMatchers.put(key, JsonEquivalenceMatchers.equivalentJsonNode(json));
			return this;
		}

		public ObjectNodeMatcher withAnyOtherProperties() {
			failOnUnexpectedProperties = false;
			return this;
		}
	}

	public static Matcher<TextNode> jsonString(String value) {
		return jsonString(equalTo(value));
	}

	public static Matcher<TextNode> jsonString(Matcher<String> valueMatcher) {
		return new TypeSafeDiagnosingMatcher<TextNode>() {
			@Override
			protected boolean matchesSafely(TextNode item, Description mismatchDescription) {
				if (!valueMatcher.matches(item.asText())) {
					mismatchDescription.appendText("text was: ").appendValue(item.asText());
					return false;
				}
				return true;
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("text ").appendDescriptionOf(valueMatcher);
			}
		};
	}

	public static Matcher<JsonNode> jsonAny() {
		return any(JsonNode.class);
	}

	public static Matcher<NullNode> jsonNull() {
		return new TypeSafeDiagnosingMatcher<NullNode>() {
			@Override
			protected boolean matchesSafely(NullNode item, Description mismatchDescription) {
				return true;
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("null");
			}
		};
	}

	public static Matcher<NumericNode> jsonNumber(long n) {
		return jsonNumberLong(equalTo(n));
	}

	public static Matcher<NumericNode> jsonNumberLong(Matcher<Long> matcher) {
		return new TypeSafeDiagnosingMatcher<NumericNode>() {
			@Override
			protected boolean matchesSafely(NumericNode item, Description mismatchDescription) {
				long value = item.asLong();
				mismatchDescription.appendText("long value ");
				matcher.describeMismatch(value, mismatchDescription);
				return matcher.matches(value);
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("number ").appendDescriptionOf(matcher);
			}
		};
	}

	public static Matcher<NumericNode> jsonNumber(Matcher<Double> matcher) {
		return new TypeSafeDiagnosingMatcher<NumericNode>() {
			@Override
			protected boolean matchesSafely(NumericNode item, Description mismatchDescription) {
				double value = item.asDouble();
				mismatchDescription.appendText("numeric value ");
				matcher.describeMismatch(value, mismatchDescription);
				return matcher.matches(value);
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("JSON numeric ").appendDescriptionOf(matcher);
			}
		};
	}

	public static Matcher<IntNode> jsonInt(int n) {
		return jsonInt(equalTo(n));
	}

	public static Matcher<IntNode> jsonInt(Matcher<Integer> matcher) {
		return new TypeSafeDiagnosingMatcher<IntNode>() {
			@Override
			protected boolean matchesSafely(IntNode item, Description mismatchDescription) {
				int value = item.asInt();
				mismatchDescription.appendText("integer value ");
				matcher.describeMismatch(value, mismatchDescription);
				return matcher.matches(value);
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("int ").appendDescriptionOf(matcher);
			}
		};
	}

	public static Matcher<LongNode> jsonLong(long n) {
		return jsonLong(equalTo(n));
	}

	public static Matcher<LongNode> jsonLong(Matcher<Long> matcher) {
		return new TypeSafeDiagnosingMatcher<LongNode>() {
			@Override
			protected boolean matchesSafely(LongNode item, Description mismatchDescription) {
				long value = item.asLong();
				mismatchDescription.appendText("long value ");
				matcher.describeMismatch(value, mismatchDescription);
				return matcher.matches(value);
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("long ").appendDescriptionOf(matcher);
			}
		};
	}

	public static Matcher<DoubleNode> jsonDouble(double n) {
		return jsonDouble(equalTo(n));
	}

	public static Matcher<DoubleNode> jsonDouble(double n, double tolerance) {
		return jsonDouble(closeTo(n, tolerance));
	}

	public static Matcher<DoubleNode> jsonDouble(Matcher<Double> matcher) {
		return new TypeSafeDiagnosingMatcher<DoubleNode>() {
			@Override
			protected boolean matchesSafely(DoubleNode item, Description mismatchDescription) {
				double value = item.asDouble();
				mismatchDescription.appendText("double value ");
				matcher.describeMismatch(value, mismatchDescription);
				return matcher.matches(value);
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("double ").appendDescriptionOf(matcher);
			}
		};
	}

	public static Matcher<BooleanNode> jsonBoolean(boolean n) {
		return jsonBoolean(equalTo(n));
	}

	public static Matcher<BooleanNode> jsonFalse() {
		return jsonBoolean(false);
	}

	public static Matcher<BooleanNode> jsonTrue() {
		return jsonBoolean(true);
	}

	public static Matcher<BooleanNode> jsonBoolean(Matcher<Boolean> matcher) {
		return new TypeSafeDiagnosingMatcher<BooleanNode>() {
			@Override
			protected boolean matchesSafely(BooleanNode item, Description mismatchDescription) {
				boolean value = item.asBoolean();
				mismatchDescription.appendText("boolean value ");
				matcher.describeMismatch(value, mismatchDescription);
				return matcher.matches(value);
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("boolean ").appendDescriptionOf(matcher);
			}
		};
	}

	public static Matcher<JsonNode> jsonNodeStringifyingAs(String str) {
		return new TypeSafeDiagnosingMatcher<JsonNode>() {
			@Override
			protected boolean matchesSafely(JsonNode item, Description mismatchDescription) {
				String itemString = item.toString();
				if (!itemString.equals(str)) {
					mismatchDescription.appendText("node is ").appendValue(itemString);
					return false;
				}
				return true;
			}

			@Override
			public void describeTo(Description description) {
				description.appendValue(str);
			}
		};
	}

	public static class ArrayNodeMatcher extends TypeSafeDiagnosingMatcher<ArrayNode> {
		private Matcher<Iterable<? extends JsonNode>> contentsMatcher = Matchers.emptyIterable();

		@Override
		protected boolean matchesSafely(final ArrayNode item, Description mismatchDescription) {
			Iterable<JsonNode> iterable = new Iterable<JsonNode>() {
				@Override
				public Iterator<JsonNode> iterator() {
					return item.elements();
				}
			};
			if (!contentsMatcher.matches(iterable)) {
				contentsMatcher.describeMismatch(iterable, mismatchDescription);
				return false;
			}
			return true;
		}

		@Override
		public void describeTo(Description description) {
			description.appendText("array");
			if (contentsMatcher != null) {
				description.appendText(" containing ").appendDescriptionOf(contentsMatcher);
			}
		}

		@SafeVarargs
		public final ArrayNodeMatcher of(Matcher<? extends JsonNode>... nodeMatchers) {
			if (nodeMatchers.length == 0) {
				contentsMatcher = Matchers.emptyIterable();
				return this;
			}
			@SuppressWarnings({ "unchecked", "rawtypes" })
			List<Matcher<? super JsonNode>> matcherList = (List) Arrays.asList(nodeMatchers);
			contentsMatcher = IsIterableContainingInOrder.contains(matcherList);
			return this;
		}

		@SafeVarargs
		public final ArrayNodeMatcher inAnyOrder(Matcher<? extends JsonNode>... nodeMatchers) {
			if (nodeMatchers.length == 0) {
				contentsMatcher = Matchers.emptyIterable();
				return this;
			}
			@SuppressWarnings({ "unchecked", "rawtypes" })
			List<Matcher<? super JsonNode>> matcherList = (List) Arrays.asList(nodeMatchers);
			contentsMatcher = IsIterableContainingInAnyOrder.containsInAnyOrder(matcherList);
			return this;
		}

		public ArrayNodeMatcher including(final Matcher<? extends JsonNode> nodeMatcher) {
			contentsMatcher = new TypeSafeDiagnosingMatcher<Iterable<? extends JsonNode>>() {
				@Override
				protected boolean matchesSafely(Iterable<? extends JsonNode> item, Description mismatchDescription) {
					Iterator<? extends JsonNode> iterator = item.iterator();
					if (!iterator.hasNext()) {
						mismatchDescription.appendText("array was empty");
						return false;
					}
					while (iterator.hasNext()) {
						JsonNode node = iterator.next();
						if (nodeMatcher.matches(node)) return true;
					}
					mismatchDescription.appendText("not matched: ").appendDescriptionOf(nodeMatcher);
					return false;
				}

				@Override
				public void describeTo(Description description) {
					description.appendDescriptionOf(nodeMatcher);
				}
			};
			return this;
		}
	}

	public static ArrayNodeMatcher jsonArray() {
		return new ArrayNodeMatcher();
	}

	private JsonStructureMatchers() {
	}
}

package org.araqnid.eventstore.testutil;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import static org.hamcrest.Matchers.equalTo;

public final class JsonEquivalenceMatchers {
	private static final ObjectMapper STRICT_MAPPER = new ObjectMapper();
	private static final ObjectMapper LAX_MAPPER = new ObjectMapper().enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES)
			.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);

	public static Matcher<JsonNode> equivalentJsonNode(String expectedJsonSource) {
		return _equivalentTo(expectedJsonSource, LAX_MAPPER::readTree, (JsonNode n) -> n);
	}

	public static Matcher<String> equivalentTo(String expectedJsonSource) {
		return _textEquivalentTo(expectedJsonSource, LAX_MAPPER::readTree);
	}

	public static Matcher<String> equivalentTo(byte[] expectedJsonSource) {
		return _textEquivalentTo(expectedJsonSource, LAX_MAPPER::readTree);
	}

	public static Matcher<byte[]> bytesEquivalentTo(String expectedJsonSource) {
		return _bytesEquivalentTo(expectedJsonSource, LAX_MAPPER::readTree);
	}

	public static Matcher<byte[]> bytesEquivalentTo(byte[] expectedJsonSource) {
		return _bytesEquivalentTo(expectedJsonSource, LAX_MAPPER::readTree);
	}

	private static <T> Matcher<String> _textEquivalentTo(T expectedJsonSource, EquivalenceParser<? super T> parser) {
		return _equivalentTo(expectedJsonSource, parser, STRICT_MAPPER::readTree);
	}

	private static <T> Matcher<byte[]> _bytesEquivalentTo(T expectedJsonSource, EquivalenceParser<? super T> parser) {
		return _equivalentTo(expectedJsonSource, parser, STRICT_MAPPER::readTree);
	}

	private static <Expected, Actual> Matcher<Actual> _equivalentTo(Expected expectedJsonSource, EquivalenceParser<? super Expected> expectedJsonParser, EquivalenceParser<? super Actual> actualJsonParser) {
		JsonNode expectedJson;
		try {
			expectedJson = expectedJsonParser.parse(expectedJsonSource);
		} catch (IOException e) {
			throw new IllegalArgumentException("Invalid reference JSON", e);
		}
		return new TypeSafeDiagnosingMatcher<Actual>() {
			private final Matcher<JsonNode> matcher = equalTo(expectedJson);

			@Override
			protected boolean matchesSafely(Actual item, Description mismatchDescription) {
				JsonNode actualJson;
				try {
					actualJson = actualJsonParser.parse(item);
				} catch (IOException e) {
					mismatchDescription.appendText("Invalid JSON: ").appendValue(e);
					return false;
				}
				matcher.describeMismatch(actualJson, mismatchDescription);
				return matcher.matches(actualJson);
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("JSON ").appendValue(expectedJsonSource);
			}
		};
	}

	private interface EquivalenceParser<T> {
		JsonNode parse(T expectedJsonSource) throws IOException;
	}

	private JsonEquivalenceMatchers() {
	}
}

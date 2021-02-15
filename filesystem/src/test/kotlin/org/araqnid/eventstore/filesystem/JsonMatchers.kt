package org.araqnid.eventstore.filesystem

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.araqnid.kotlin.assertthat.AssertionResult
import org.araqnid.kotlin.assertthat.Matcher
import kotlin.text.Charsets.UTF_8

private val format = Json {
    isLenient = true
    prettyPrint = true
}

fun bytesEquivalentTo(referenceJson: String): Matcher<ByteArray> {
    val referenceJsonElement = format.parseToJsonElement(referenceJson)
    return object : Matcher<ByteArray> {
        override fun match(actual: ByteArray): AssertionResult {
            val actualJsonElement = Json.parseToJsonElement(actual.toString(UTF_8))
            println("referenceJsonElement: $referenceJsonElement")
            println("actualJsonElement: $actualJsonElement")
            println("match? ${actualJsonElement == referenceJsonElement}")
            return if (actualJsonElement == referenceJsonElement) AssertionResult.Match
            else AssertionResult.Mismatch("JSON was ${format.encodeToString(actualJsonElement)}")
        }

        override val description by lazy { "JSON binary equivalent to ${format.encodeToString(referenceJsonElement)}" }
    }
}

fun equivalentTo(referenceJson: String): Matcher<String> {
    val referenceJsonElement = format.parseToJsonElement(referenceJson)
    return object : Matcher<String> {
        override fun match(actual: String): AssertionResult {
            val actualJsonElement = Json.parseToJsonElement(actual)
            return if (actualJsonElement == referenceJsonElement) AssertionResult.Match
            else AssertionResult.Mismatch("JSON was ${format.encodeToString(actualJsonElement)}")
        }

        override val description by lazy { "JSON text equivalent to ${format.encodeToString(referenceJsonElement)}" }
    }
}

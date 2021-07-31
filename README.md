Event store
===========

[ ![Kotlin](https://img.shields.io/badge/kotlin-1.4.30-blue.svg)](http://kotlinlang.org)
[![Kotlin JS IR supported](https://img.shields.io/badge/Kotlin%2FJS-IR%20supported-yellow)](https://kotl.in/jsirsupported)
[![Gradle Build](https://github.com/araqnid/eventstore/actions/workflows/gradle-build.yml/badge.svg)](https://github.com/araqnid/eventstore/actions/workflows/gradle-build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.araqnid.eventstore/eventstore-api.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.araqnid.eventstore%22%20AND%20a%3A%22eventstore-api%22)

Provides an API (but only limited implementation) for storing events in sequence so that applications can replay them,
in an Event-Sourcing architecture. Includes a service for subscribing to an event store and continuously delivering
incoming events, with hooks for applications to load and save snapshots of their state built from it.

Related projects
----------------

Pretty much just a Kotlinisation of tg-eventstore: https://github.com/tim-group/tg-eventstore

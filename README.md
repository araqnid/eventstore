Event store
===========

[ ![Kotlin](https://img.shields.io/badge/kotlin-1.4.30-blue.svg)](http://kotlinlang.org)
[![Kotlin JS IR supported](https://img.shields.io/badge/Kotlin%2FJS-IR%20supported-yellow)](https://kotl.in/jsirsupported)

Provides an API (but only limited implementation) for storing events in sequence so that applications can replay them,
in an Event-Sourcing architecture. Includes a service for subscribing to an event store and continuously delivering
incoming events, with hooks for applications to load and save snapshots of their state built from it.

Related projects
----------------

Pretty much just a Kotlinisation of tg-eventstore: https://github.com/tim-group/tg-eventstore

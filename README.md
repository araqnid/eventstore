Event store
===========

[ ![Build Status](https://travis-ci.org/araqnid/eventstore.svg?branch=master)](https://travis-ci.org/araqnid/eventstore) [ ![Download](https://api.bintray.com/packages/araqnid/maven/eventstore/images/download.svg) ](https://bintray.com/araqnid/maven/eventstore/_latestVersion) [ ![Kotlin](https://img.shields.io/badge/kotlin-1.2.0-blue.svg)](http://kotlinlang.org)

Provides an API (but only limited implementation) for storing events in sequence so that applications can replay them,
in an Event-Sourcing architecture. Includes a service for subscribing to an event store and continuously delivering
incoming events, with hooks for applications to load and save snapshots of their state built from it.

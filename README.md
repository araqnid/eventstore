Event store
===========

[ ![Build Status](https://travis-ci.org/araqnid/eventstore.svg?branch=master)](https://travis-ci.org/araqnid/eventstore) [ ![Download](https://api.bintray.com/packages/araqnid/maven/eventstore/images/download.svg) ](https://bintray.com/araqnid/maven/eventstore/_latestVersion) [ ![Kotlin](https://img.shields.io/badge/kotlin-1.2.0-blue.svg)](http://kotlinlang.org)

Provides an API (but only limited implementation) for storing events in sequence so that applications can replay them,
in an Event-Sourcing architecture. Includes a service for subscribing to an event store and continuously delivering
incoming events, with hooks for applications to load and save snapshots of their state built from it.

Example app
-----------

The app on https://fuel.araqnid.org/

- See the source at https://github.com/araqnid/fuel-log/

Get the library
---------------

Eventstore is published on [JCenter](https://bintray.com/bintray/jcenter). You need something like this in
`build.gradle` or `build.gradle.kts`:

```kotlin
repositories {
    jcenter()
}
dependencies {
    compile("org.araqnid:eventstore:0.0.22")
}
```
 
Related projects
----------------

Pretty much just a Kotlinisation of tg-eventstore: https://github.com/tim-group/tg-eventstore

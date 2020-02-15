plugins {
    kotlin("jvm") version "1.3.61" apply false
    id("com.jfrog.bintray") version "1.8.4" apply false
}

allprojects {
    group = "org.araqnid.eventstore"

    repositories {
        jcenter()
    }
}

LibraryVersions.toMap().forEach { (name, value) ->
    ext["${name}Version"] = value
}

plugins {
    kotlin("jvm") version "1.4.30" apply false
}

val buildNumber: String? = System.getenv("BUILD_NUMBER")
val versionPrefix = "0.1"

allprojects {
    group = "org.araqnid.eventstore"
    if (buildNumber != null)
        version = "${versionPrefix}.${buildNumber}"

    repositories {
        jcenter()
    }
}

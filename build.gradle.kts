plugins {
    kotlin("jvm") version "1.7.0" apply false
}

allprojects {
    group = "org.araqnid.eventstore"
    version = "0.2.0"
}

subprojects {
    pluginManager.apply("base")

    the<BasePluginExtension>().apply {
        archivesName.set("eventstore${project.path.replace(':', '-')}")
    }

    repositories {
        mavenCentral()
    }
}

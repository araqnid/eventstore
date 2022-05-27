plugins {
    kotlin("jvm") version "1.6.21" apply false
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

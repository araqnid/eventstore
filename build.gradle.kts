plugins {
    kotlin("jvm") version "1.5.31" apply false
}

allprojects {
    group = "org.araqnid.eventstore"
    version = "0.1.27"
}

subprojects {
    pluginManager.apply("base")

    the<BasePluginExtension>().apply {
        archivesName.set("eventstore${project.path.replace(':', '-')}")
    }

    repositories {
        mavenCentral()
        jcenter() // for kotlinx-nodejs
    }
}

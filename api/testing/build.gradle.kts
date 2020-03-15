plugins {
    kotlin("jvm")
    `maven-publish`
    `java-library`
    id("com.jfrog.bintray")
}

dependencies {
    api(project(":api"))
    api(kotlin("test-junit"))
    api("junit:junit:4.13")
    implementation("com.natpryce:hamkrest:${LibraryVersions.hamkrest}")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

tasks {
    "jar"(Jar::class) {
        manifest {
            attributes["Implementation-Title"] = project.description ?: project.name
            attributes["Implementation-Version"] = project.version
            attributes["Automatic-Module-Name"] = "org.araqnid.eventstore.apitesting"
        }
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            artifactId = "eventstore-api-testing"
            from(components["java"])
        }
    }
}

bintray {
    user = (project.properties["bintray.user"] ?: "").toString()
    key = (project.properties["bintray.apiKey"] ?: "").toString()
    publish = true
    setPublications("mavenJava")
    pkg.repo = "maven"
    pkg.name = "eventstore"
    pkg.setLicenses("Apache-2.0")
    pkg.vcsUrl = "https://github.com/araqnid/eventstore"
    pkg.desc = "Store and replay sequences of events"
    if (version != Project.DEFAULT_VERSION) {
        pkg.version.name = version.toString()
        pkg.version.vcsTag = "v$version"
    }
}

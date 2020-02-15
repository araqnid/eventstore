plugins {
    kotlin("jvm") version "1.3.61"
    `maven-publish`
    `java-library`
    id("com.jfrog.bintray") version "1.8.4"
}

group = "org.araqnid.eventstore"

repositories {
    jcenter()
}

LibraryVersions.toMap().forEach { (name, value) ->
    ext["${name}Version"] = value
}

dependencies {
    api("com.google.guava:guava:${LibraryVersions.guava}")
    implementation("org.slf4j:slf4j-api:1.7.25")
    implementation("org.tukaani:xz:1.6")
    implementation("org.apache.commons:commons-compress:1.15")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-guava:${LibraryVersions.jackson}")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:${LibraryVersions.jackson}")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${LibraryVersions.jackson}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${LibraryVersions.jackson}")
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test-junit"))
    testImplementation("org.mockito:mockito-core:3.2.4")
    testImplementation("com.timgroup:clocks-testing:1.0.1070")
    testImplementation("com.natpryce:hamkrest:1.4.2.2")
    testImplementation("org.araqnid:hamkrest-json:1.0.3")
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
            attributes["Automatic-Module-Name"] = "org.araqnid.eventstore"
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

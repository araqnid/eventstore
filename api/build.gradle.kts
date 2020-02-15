plugins {
    kotlin("jvm")
    `maven-publish`
    `java-library`
    id("com.jfrog.bintray")
}

LibraryVersions.toMap().forEach { (name, value) ->
    ext["${name}Version"] = value
}

dependencies {
    api("com.google.guava:guava:${LibraryVersions.guava}")
    api(kotlin("stdlib-jdk8"))
    implementation("org.slf4j:slf4j-api:${LibraryVersions.slf4j}")
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test-junit"))
    testImplementation("org.mockito:mockito-core:3.2.4")
    testImplementation("com.timgroup:clocks-testing:1.0.1070")
    testImplementation("com.natpryce:hamkrest:1.4.2.2")
    testImplementation("org.araqnid:hamkrest-json:1.0.3")
    testImplementation(project(":api:testing"))
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
            attributes["Automatic-Module-Name"] = "org.araqnid.eventstore.api"
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
            artifactId = "eventstore-api"
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

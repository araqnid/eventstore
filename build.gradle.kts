import java.io.ByteArrayOutputStream

plugins {
    kotlin("jvm") version "1.2.0"
    `maven-publish`
    id("com.jfrog.bintray") version "1.7.3"
}

val gitVersion by extra {
    val capture = ByteArrayOutputStream()
    project.exec {
        commandLine("git", "describe", "--tags")
        standardOutput = capture
    }
    String(capture.toByteArray())
            .trim()
            .removePrefix("v")
            .replace('-', '.')
}

group = "org.araqnid"
version = gitVersion

val guavaVersion by extra("23.5-jre")
val jacksonVersion by extra("2.8.7")

repositories {
    jcenter()
}

dependencies {
    compile("com.google.guava:guava:$guavaVersion")
    compile("org.slf4j:slf4j-api:1.7.25")
    compile("org.tukaani:xz:1.5")
    compile("org.apache.commons:commons-compress:1.13")
    compile("com.fasterxml.jackson.datatype:jackson-datatype-guava:$jacksonVersion")
    compile("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
    compile("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    compile("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation(kotlin("stdlib-jdk8", "1.2.0"))
    implementation(kotlin("reflect", "1.2.0"))
    implementation("com.google.code.findbugs:jsr305:3.0.0")
    testCompile(kotlin("test-junit", "1.2.0"))
    testCompile("org.mockito:mockito-core:2.7.21")
    testCompile("com.timgroup:clocks-testing:1.0.1070")
    testCompile("com.natpryce:hamkrest:1.4.2.2")
    testCompile("org.araqnid:hamkrest-json:1.0.3")
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "1.8"
        sourceCompatibility = "1.8"
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
        options.isIncremental = true
        options.isDeprecation = true
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    "jar"(Jar::class) {
        manifest {
            attributes["Implementation-Title"] = project.description ?: project.name
            attributes["Implementation-Version"] = project.version
        }
    }
}

val sourcesJar by tasks.creating(Jar::class) {
    classifier = "sources"
    from(java.sourceSets["main"].allSource)
}

publishing {
    (publications) {
        "mavenJava"(MavenPublication::class) {
            from(components["java"])
            artifact(sourcesJar)
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
    pkg.version.name = gitVersion
    if (!gitVersion.contains(".g")) {
        pkg.version.vcsTag = "v" + gitVersion
    }
}

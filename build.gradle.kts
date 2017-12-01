import java.io.ByteArrayOutputStream

plugins {
    kotlin("jvm") version "1.2.0"
    `maven-publish`
}

group = "org.araqnid"
version = (fun (): String {
    val capture = ByteArrayOutputStream()
    project.exec {
        commandLine("git", "describe", "--tags")
        standardOutput = capture
    }
    return String(capture.toByteArray())
            .trim()
            .removePrefix("v")
            .replace('-', '.')
})()

val guavaVersion by extra { "23.5-jre" }
val jacksonVersion by extra { "2.8.7" }

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "1.9"
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
    repositories {
        maven(url = "https://repo.araqnid.org/maven/") {
            credentials {
                username = "repo-user"
                password = "repo-password"
            }
        }
    }
    (publications) {
        "mavenJava"(MavenPublication::class) {
            from(components["java"])
            artifact(sourcesJar)
        }
    }
}

repositories {
    mavenCentral()
    maven(url = "https://repo.araqnid.org/maven/")
}

dependencies {
    compile("com.google.guava:guava:$guavaVersion")
    compile("org.slf4j:slf4j-api:1.7.25")
    compile(kotlin("stdlib-jdk8"))
    compile(kotlin("reflect"))
    compile("com.google.code.findbugs:jsr305:3.0.0")
    compile("org.tukaani:xz:1.5")
    compile("org.apache.commons:commons-compress:1.13")
    compile("com.fasterxml.jackson.datatype:jackson-datatype-guava:$jacksonVersion")
    compile("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
    compile("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    compile("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    testCompile("junit:junit:4.12")
    testCompile("org.hamcrest:hamcrest-library:1.3")
    testCompile("org.mockito:mockito-core:2.7.21")
    testCompile("com.timgroup:clocks-testing:1.0.1070")
}

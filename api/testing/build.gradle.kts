plugins {
    kotlin("jvm")
    `maven-publish`
    `java-library`
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

publishing {
    repositories {
        maven(url = "https://maven.pkg.github.com/araqnid/eventstore") {
            name = "github"
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

plugins {
    kotlin("jvm")
    `maven-publish`
    `java-library`
}

repositories {
    jcenter()
}

dependencies {
    api(project(":api"))
    implementation("org.slf4j:slf4j-api:${LibraryVersions.slf4j}")
    implementation("org.tukaani:xz:1.6")
    implementation("org.apache.commons:commons-compress:1.15")
    implementation("com.fasterxml.jackson.core:jackson-core:${LibraryVersions.jackson}")
    implementation(platform(kotlin("bom")))
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13")
    testImplementation(project(":api:testing"))
    testImplementation("com.timgroup:clocks-testing:1.0.1070")
    testImplementation("org.araqnid.kotlin.assert-that:assert-that:${LibraryVersions.assertThat}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${LibraryVersions.serialization}")
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
            attributes["Automatic-Module-Name"] = "org.araqnid.eventstore.filesystem"
        }
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }
    withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>>().configureEach {
        kotlinOptions {
            freeCompilerArgs += listOf("-Xopt-in=kotlinx.coroutines.FlowPreview")
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "eventstore${project.path.replace(':', '-')}"
        }
    }

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

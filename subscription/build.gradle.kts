plugins {
    kotlin("jvm")
    `maven-publish`
    `java-library`
}

dependencies {
    api(project(":api"))
    implementation("org.slf4j:slf4j-api:${LibraryVersions.slf4j}")
    implementation("org.tukaani:xz:1.6")
    implementation("org.apache.commons:commons-compress:1.15")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-guava:${LibraryVersions.jackson}")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:${LibraryVersions.jackson}")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${LibraryVersions.jackson}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${LibraryVersions.jackson}")
    implementation(platform(kotlin("bom")))
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13")
    testImplementation("org.mockito:mockito-core:3.2.4")
    testImplementation("com.timgroup:clocks-testing:1.0.1070")
    testImplementation("org.araqnid.kotlin.assert-that:assert-that:${LibraryVersions.assertThat}")
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
            attributes["Automatic-Module-Name"] = "org.araqnid.eventstore.subscription"
        }
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "1.8"
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

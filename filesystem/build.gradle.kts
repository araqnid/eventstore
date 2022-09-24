import java.net.URI

plugins {
    kotlin("jvm")
    `maven-publish`
    signing
}

description = "Event store based on storing events in local filesystem"

dependencies {
    api(project(":api"))
    implementation("org.slf4j:slf4j-api:2.0.2")
    implementation("org.tukaani:xz:1.9")
    implementation("org.apache.commons:commons-compress:1.21")
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.12.5"))
    implementation("com.fasterxml.jackson.core:jackson-core")
    implementation(platform(kotlin("bom")))
    testImplementation(kotlin("test-junit"))
    constraints {
        testImplementation("junit:junit:4.13.2")
    }
    testImplementation(project(":api:testing"))
    testImplementation("com.timgroup:clocks-testing:1.0.1088")
    testImplementation("org.araqnid.kotlin.assert-that:assert-that:0.1.1")
    testImplementation(platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.3.3"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
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
            freeCompilerArgs += listOf("-opt-in=kotlinx.coroutines.FlowPreview")
        }
    }
}

val javadocJar = tasks.register("javadocJar", Jar::class.java) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "eventstore${project.path.replace(':', '-')}"
            artifact(javadocJar)
            pom {
                name.set(project.name)
                description.set(project.description)
                licenses {
                    license {
                        name.set("Apache")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                url.set("https://github.com/araqnid/eventstore")
                issueManagement {
                    system.set("Github")
                    url.set("https://github.com/araqnid/eventstore/issues")
                }
                scm {
                    connection.set("https://github.com/araqnid/eventstore.git")
                    url.set("https://github.com/araqnid/eventstore")
                }
                developers {
                    developer {
                        name.set("Steven Haslam")
                        email.set("araqnid@gmail.com")
                    }
                }
            }
        }
    }

    repositories {
        val sonatypeUser: String? by project
        if (sonatypeUser != null) {
            maven {
                name = "OSSRH"
                url = URI("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
                val sonatypePassword: String by project
                credentials {
                    username = sonatypeUser
                    password = sonatypePassword
                }
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}

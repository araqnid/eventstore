import java.net.URI

plugins {
    kotlin("jvm")
    `maven-publish`
    signing
}

description = "Eventstore API and empty/in-memory implementations"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    api(kotlin("stdlib-jdk8"))
    api(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.2"))
    api("org.jetbrains.kotlinx:kotlinx-datetime:0.3.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")
    api("com.google.guava:guava:31.1-jre")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.2")

    testImplementation(kotlin("test-junit"))
    testImplementation(project(":api:testing"))
    constraints {
        testImplementation("junit:junit:4.13.2")
    }
}

tasks {
    named("jar", Jar::class).configure {
        manifest {
            attributes["Implementation-Title"] = project.description ?: project.name
            attributes["Implementation-Version"] = project.version
            attributes["Automatic-Module-Name"] = "org.araqnid.eventstore.api"
        }
    }

    withType<org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile>().configureEach {
        kotlinOptions.jvmTarget = "1.8"
    }

    withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>>().configureEach {
        kotlinOptions.freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }
}

val javadocJar = tasks.register("javadocJar", Jar::class.java) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        withType<MavenPublication> {
            if (!artifactId.startsWith("eventstore"))
                artifactId = "eventstore-$artifactId"
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

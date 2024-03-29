import java.net.URI

plugins {
    kotlin("jvm")
    `maven-publish`
    signing
}

description = "Abstract tests to run against event store implementations to check API compliance"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

dependencies {
    api(project(":api"))
    implementation("org.araqnid.kotlin.assert-that:assert-that:0.1.1")
    api(kotlin("test-junit"))
    constraints {
        api("junit:junit:4.13.2")
    }
}

tasks {
    "jar"(Jar::class) {
        manifest {
            attributes["Implementation-Title"] = project.description ?: project.name
            attributes["Implementation-Version"] = project.version
            attributes["Automatic-Module-Name"] = "org.araqnid.eventstore.apitesting"
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
        register<MavenPublication>("mavenJava") {
            from(components["java"])
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

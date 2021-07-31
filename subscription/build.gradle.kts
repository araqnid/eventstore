import java.net.URI

plugins {
    kotlin("jvm")
    `maven-publish`
    signing
}

description = "Service to chase event store and read/write snapshots"

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
    testImplementation("junit:junit:4.13.2")
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

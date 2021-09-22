import java.net.URI

plugins {
    kotlin("multiplatform")
    `maven-publish`
    signing
}

description = "Eventstore API and empty/in-memory implementations"

kotlin {
    jvm { }
    js(IR) {
        nodejs { }
        useCommonJs()
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    commonMainApi(kotlin("stdlib-common"))
    commonMainApi("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    commonMainApi("org.jetbrains.kotlinx:kotlinx-datetime:0.2.1")

    "jvmMainApi"(kotlin("stdlib-jdk8"))
    "jvmMainApi"("com.google.guava:guava:30.1.1-jre")
    "jvmMainApi"("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.5.2")

    "jsMainApi"(kotlin("stdlib-js"))
    "jsMainImplementation"("org.jetbrains.kotlinx:kotlinx-nodejs:0.0.7")

    commonTestImplementation(kotlin("test-common"))
    commonTestImplementation(kotlin("test-annotations-common"))
    commonTestImplementation(project(":api:testing"))

    "jvmTestImplementation"(kotlin("test-junit"))
    constraints {
        "jvmTestImplementation"("junit:junit:4.13.2")
    }

    "jsTestImplementation"(kotlin("test-js"))
}

tasks {
    "jvmJar"(Jar::class) {
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
        kotlinOptions.freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
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

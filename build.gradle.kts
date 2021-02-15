plugins {
    kotlin("jvm") version "1.4.30" apply false
}

val buildNumber: String? = System.getenv("BUILD_NUMBER")
val versionPrefix = "0.1"

allprojects {
    group = "org.araqnid.eventstore"
    if (buildNumber != null)
        version = "${versionPrefix}.${buildNumber}"

    repositories {
        mavenCentral()

        repositories {
            maven(url = "https://maven.pkg.github.com/araqnid/assert-that") {
                name = "github"
                credentials {
                    username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                    password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}

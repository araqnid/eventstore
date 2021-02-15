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
            if (isGithubUserAvailable(project)) {
                for (repo in listOf("assert-that")) {
                    maven(url = "https://maven.pkg.github.com/araqnid/$repo") {
                        name = "github-$repo"
                        credentials(githubUserCredentials(project))
                    }
                }
            }
        }
    }
}

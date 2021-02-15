plugins {
    kotlin("jvm") version "1.4.30" apply false
}

allprojects {
    group = "org.araqnid.eventstore"
    version = "0.1.26"
}

subprojects {
    pluginManager.apply("base")

    the<BasePluginConvention>().archivesBaseName = "eventstore${project.path.replace(':', '-')}"

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

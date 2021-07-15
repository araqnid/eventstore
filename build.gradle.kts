plugins {
    kotlin("jvm") version "1.5.21" apply false
}

allprojects {
    group = "org.araqnid.eventstore"
    version = "0.1.27"
}

subprojects {
    pluginManager.apply("base")

    the<BasePluginConvention>().archivesBaseName = "eventstore${project.path.replace(':', '-')}"

    repositories {
        mavenCentral()
        jcenter() // for kotlinx-nodejs

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

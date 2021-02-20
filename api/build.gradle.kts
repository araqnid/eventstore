plugins {
    kotlin("multiplatform")
    `maven-publish`
}

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
    commonMainApi("org.jetbrains.kotlinx:kotlinx-coroutines-core:${LibraryVersions.coroutines}")
    commonMainApi("org.jetbrains.kotlinx:kotlinx-datetime:0.1.1")

    "jvmMainApi"(kotlin("stdlib-jdk8"))
    "jvmMainApi"("com.google.guava:guava:${LibraryVersions.guava}")
    "jvmMainApi"("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${LibraryVersions.coroutines}")

    "jsMainApi"(kotlin("stdlib-js"))
    "jsMainImplementation"("org.jetbrains.kotlinx:kotlinx-nodejs:0.0.7")

    commonTestImplementation(kotlin("test-common"))
    commonTestImplementation(kotlin("test-annotations-common"))
    commonTestImplementation(project(":api:testing"))

    "jvmTestImplementation"(kotlin("test-junit"))
    "jvmTestImplementation"("junit:junit:4.13")

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

publishing {
    repositories {
        maven(url = "https://maven.pkg.github.com/araqnid/eventstore") {
            name = "github"
            credentials(githubUserCredentials(project))
        }
    }
}

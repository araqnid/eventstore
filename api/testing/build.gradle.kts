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
    commonMainApi(project(":api"))
    commonMainImplementation("org.araqnid.kotlin.assert-that:assert-that:${LibraryVersions.assertThat}")
    commonMainImplementation(kotlin("test-common"))
    commonMainImplementation(kotlin("test-annotations-common"))
    "jvmMainApi"(kotlin("test-junit"))
    "jvmMainApi"("junit:junit:4.13")
}

tasks {
    "jvmJar"(Jar::class) {
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

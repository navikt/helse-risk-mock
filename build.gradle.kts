val junitJupiterVersion = "5.10.0"
val ktorVersion = "2.3.7"
val rapidsAndRiversVersion = "2024010209171704183456.6d035b91ffb4"

plugins {
    kotlin("jvm") version "1.9.22"
}

val githubUser: String by project
val githubPassword: String by project
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/navikt/*")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
}

tasks {
    kotlin {
        jvmToolchain(21)
    }

    named<Jar>("jar") {
        archiveBaseName.set("app")

        manifest {
            attributes["Main-Class"] = "no.nav.helse.riskmock.AppKt"
            attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
                it.name
            }
        }
    }
    val copyDeps by registering(Sync::class) {
        from(configurations.runtimeClasspath)
        into("libs")
    }
    named("assemble") {
        dependsOn(copyDeps)
    }

    named<Test>("test") {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    wrapper {
        gradleVersion = "8.5"
    }
}

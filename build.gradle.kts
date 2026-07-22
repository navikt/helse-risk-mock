plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
}

private val ktorVersion = "3.5.1"
private val tbdLibsVersion = "20260526.1506"

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:2026051812441779101082")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")

    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.github.navikt.tbd-libs:rapids-and-rivers-test:$tbdLibsVersion")
    testImplementation("com.github.navikt.tbd-libs:jackson:$tbdLibsVersion")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    build {
        doLast {
            val erLokaltBygg = !System.getenv().containsKey("GITHUB_ACTION")
            val manglerPreCommitHook = !File(".git/hooks/pre-commit").isFile
            if (erLokaltBygg && manglerPreCommitHook) {
                println(
                    """
                    !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! ¯\_(⊙︿⊙)_/¯ !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                    !            Hei du! Det ser ut til at du mangler en pre-commit-hook :/         !
                    ! Du kan installere den ved å kjøre './gradlew addKtlintFormatGitPreCommitHook' !
                    !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                    """.trimIndent(),
                )
            }
        }
    }
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
    named<Jar>("jar") {
        archiveBaseName.set("app")

        manifest {
            attributes["Main-Class"] = "no.nav.helse.riskmock.AppKt"
            attributes["Class-Path"] =
                configurations.runtimeClasspath.get().joinToString(separator = " ") {
                    it.name
                }
        }
    }
    val copyDeps =
        register<Sync>("copyDeps") {
            description = "Kopierer alle avhengigheter til libs-mappa"
            from(configurations.runtimeClasspath)
            into(layout.buildDirectory.dir("libs"))
        }
    named("assemble") {
        dependsOn(copyDeps)
    }
}

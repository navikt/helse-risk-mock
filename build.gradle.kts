plugins {
    alias(libs.plugins.sas.deployable)
}

sasDeployable {
    mainClass = "no.nav.helse.riskmock.AppKt"
}

dependencies {
    implementation(libs.rapidsAndRivers)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.ktor.server.contentNegotiation)

    testImplementation(libs.tbdLibs.rapidsAndRiversTest)
    testImplementation(libs.tbdLibs.jackson)
}

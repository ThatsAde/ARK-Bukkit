plugins {
    id("breach.module-conventions")
}

group = "org.breachsmp"
version = "1.0-SNAPSHOT"

dependencies {
    api(project(":core-api"))
    implementation(project(":core-domain"))
    implementation(libs.caffeine)

    // TestDatabase, MutableClock e i servizi di piattaforma per gli IT (solo test: non viola I7)
    testImplementation(testFixtures(project(":core-platform")))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

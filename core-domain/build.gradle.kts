plugins {
    id("breach.java-conventions")
}

group = "org.breachsmp"
version = "1.0-SNAPSHOT"

dependencies {
    api(project(":core-api"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

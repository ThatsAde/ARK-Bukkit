plugins {
    id("breach.java-conventions")
    alias(libs.plugins.shadow)
    alias(libs.plugins.runpaper)
}

group = "org.breachsmp"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":core-api"))
    implementation(project(":core-domain"))

    compileOnly(libs.paper.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
}

tasks {
    shadowJar {
        archiveClassifier = ""
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("26.2")
    }
}
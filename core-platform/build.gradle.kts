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

    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    implementation(libs.flyway.mysql)
    implementation(libs.mariadb.jdbc)

    compileOnly(libs.paper.api)

    // Paper anche nei test: fornisce SnakeYAML, Adventure e le classi Bukkit referenziate
    testImplementation(libs.paper.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.mariadb)
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

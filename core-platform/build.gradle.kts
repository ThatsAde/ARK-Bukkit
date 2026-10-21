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
    processResources {
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier = ""
        // Flyway e il driver MariaDB si trovano via ServiceLoader: i file META-INF/services vanno uniti e rilocati
        mergeServiceFiles()
        // Paper fornisce già slf4j (D-11)
        dependencies {
            exclude(dependency("org.slf4j:.*"))
        }
        val libsPackage = "org.breachsmp.core.libs"
        relocate("com.zaxxer.hikari", "$libsPackage.hikari")
        relocate("org.flywaydb", "$libsPackage.flyway")
        relocate("org.mariadb.jdbc", "$libsPackage.mariadb")
        relocate("com.fasterxml.jackson", "$libsPackage.jackson")
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("26.2")
    }
}

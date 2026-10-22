import java.util.zip.ZipFile

plugins {
    id("breach.java-conventions")
    `java-test-fixtures`
    alias(libs.plugins.shadow)
    alias(libs.plugins.runpaper)
}

group = "org.breachsmp"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":core-api"))
    implementation(project(":core-domain"))
    implementation(project(":module-economy"))

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

    // TestDatabase e MutableClock condivisi con i test dei module-* (D-22)
    testFixturesApi(libs.hikaricp)
    testFixturesImplementation(libs.flyway.core)
    testFixturesImplementation(libs.testcontainers.mariadb)
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
}

tasks {
    processResources {
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    // Il jar senza dipendenze non deve sovrascrivere quello shadow (stesso nome con classifier vuoto)
    jar {
        archiveClassifier = "plain"
    }

    shadowJar {
        archiveClassifier = ""
        // Flyway e il driver MariaDB si trovano via ServiceLoader: i file META-INF/services vanno uniti e rilocati
        // Shadow usa EXCLUDE sui duplicati: senza INCLUDE il transformer vede solo la prima copia di ogni file di servizio
        filesMatching("META-INF/services/**") {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        // La closure sopra non fa parte degli input: questa proprietà invalida la cache se la strategia cambia
        inputs.property("servicesDuplicatesStrategy", "INCLUDE")
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


    // Guardia di regressione (review finale C1): i file di servizio uniti devono contenere le voci attese, rilocate
    val verifyShadowJar = register("verifyShadowJar") {
        group = "verification"
        description = "Verifica che il jar shadow contenga i file META-INF/services uniti e rilocati (Flyway, driver MariaDB)"
        val jarFile = shadowJar.flatMap { it.archiveFile }
        dependsOn(shadowJar)
        inputs.file(jarFile)
        doLast {
            val expected = mapOf(
                "META-INF/services/org.breachsmp.core.libs.flyway.core.extensibility.Plugin" to listOf(
                    "org.breachsmp.core.libs.flyway.core.internal.scanner.classpath.ClasspathLocationHandlerImpl",
                    "org.breachsmp.core.libs.flyway.database.mysql.mariadb.MariaDBDatabaseType"
                ),
                "META-INF/services/java.sql.Driver" to listOf("org.breachsmp.core.libs.mariadb.Driver")
            )
            ZipFile(jarFile.get().asFile).use { zip ->
                val problems = mutableListOf<String>()
                expected.forEach { (path, entries) ->
                    val entry = zip.getEntry(path)
                    if (entry == null) {
                        problems += "file di servizio mancante: $path"
                    } else {
                        val lines = zip.getInputStream(entry).bufferedReader().readLines().map { it.trim() }
                        entries.filter { it !in lines }.forEach { problems += "$path non contiene $it" }
                    }
                }
                if (problems.isNotEmpty()) {
                    throw GradleException("Jar shadow non valido:\n - " + problems.joinToString("\n - "))
                }
            }
        }
    }

    check {
        dependsOn(verifyShadowJar)
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("26.2")
    }
}

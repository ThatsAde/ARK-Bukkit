plugins {
    `java-library`
}

group = "org.breachsmp"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

val itDatabaseLock = gradle.sharedServices.registerIfAbsent("itDatabaseLock", ItDatabaseLock::class.java) {
    maxParallelUsages.set(1)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // I test *IT usano il DB esterno se c'è (D-13): senza questo input Gradle riprenderebbe dalla cache l'esito "saltato"
    inputs.property("itJdbcUrl", providers.environmentVariable("BREACH_IT_JDBC_URL").orElse(""))
    usesService(itDatabaseLock)
}
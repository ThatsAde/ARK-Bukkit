plugins {
    id("breach.java-conventions")
}

// I7 (D-21): un modulo di dominio vede solo core-api e core-domain; l'unico che collega le implementazioni è core-platform
val allowedProjects = setOf(":core-api", ":core-domain")
val mainConfigurations = setOf("api", "implementation", "compileOnly", "runtimeOnly")
val modulePath = project.path

configurations.matching { it.name in mainConfigurations }.configureEach {
    val configurationName = name
    dependencies.withType<ProjectDependency>().configureEach {
        if (path !in allowedProjects) {
            throw GradleException("I7: $modulePath non può dipendere da $path in '$configurationName' " +
                    "(ammessi solo :core-api e :core-domain)")
        }
    }
}

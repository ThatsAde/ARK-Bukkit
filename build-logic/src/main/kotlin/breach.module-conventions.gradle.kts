plugins {
    id("breach.java-conventions")
}

// I7 (D-21, D-34): un modulo di dominio vede solo core-api, core-domain e core-paper-api; l'unico che collega le implementazioni è core-platform
val allowedProjects = setOf(":core-api", ":core-domain", ":core-paper-api")
val mainConfigurations = setOf("api", "implementation", "compileOnly", "runtimeOnly")
val modulePath = project.path

configurations.matching { it.name in mainConfigurations }.configureEach {
    val configurationName = name
    dependencies.withType<ProjectDependency>().configureEach {
        if (path !in allowedProjects) {
            throw GradleException("I7: $modulePath non può dipendere da $path in '$configurationName' " +
                    "(ammessi solo :core-api, :core-domain e :core-paper-api)")
        }
    }
}

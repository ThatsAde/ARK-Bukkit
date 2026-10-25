plugins {
    id("breach.java-conventions")
}

group = "org.breachsmp"
version = "1.0-SNAPSHOT"

// Ponte verso Paper per i module-* che ne hanno bisogno (D-34): niente implementazioni qui
dependencies {
    api(project(":core-api"))
    compileOnly(libs.paper.api)
}

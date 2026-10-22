import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/** Fa girare un solo task Test alla volta in tutta la build: gli *IT condividono lo schema breach_it (D-22). */
abstract class ItDatabaseLock : BuildService<BuildServiceParameters.None>

import org.gradle.api.artifacts.component.ModuleComponentIdentifier

allprojects {
    if (name == "app") {
        tasks.register("exportThirdPartyInventory") {
            doLast {
                val configs = listOf("releaseRuntimeClasspath", "debugUnitTestRuntimeClasspath", "debugAndroidTestRuntimeClasspath")
                val output = rootProject.layout.buildDirectory.file("audit/dependencies.tsv").get().asFile
                output.parentFile.mkdirs()
                output.bufferedWriter().use { writer ->
                    writer.appendLine("scope\tgroup\tartifact\tversion\tfile")
                    for (name in configs) {
                        val configuration = configurations.getByName(name)
                        for (artifact in configuration.incoming.artifactView { }.artifacts.artifacts) {
                            val id = artifact.id.componentIdentifier as? ModuleComponentIdentifier ?: continue
                            writer.appendLine(listOf(name, id.group, id.module, id.version, artifact.file.absolutePath).joinToString("\t"))
                        }
                    }
                }
                println("DEPENDENCY_INVENTORY=" + output.absolutePath)
            }
        }
    }
}

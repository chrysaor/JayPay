import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

fun Project.dockerLayer(name: String, implementation: List<Any> = emptyList(), runtimeOnly: List<Any> = emptyList()) {
    val declared = configurations.dependencyScope("${name}DockerLayerDependencies").get()
    val runtimeClasspath = configurations.getByName("runtimeClasspath")
    val layer = configurations.resolvable("${name}DockerLayer") {
        extendsFrom(declared)
        shouldResolveConsistentlyWith(runtimeClasspath)
        attributes.addAllLater(runtimeClasspath.attributes)
    }.get()
    implementation.forEach {
        dependencies.add("implementation", it)
        dependencies.add(declared.name, it)
    }
    runtimeOnly.forEach {
        dependencies.add("runtimeOnly", it)
        dependencies.add(declared.name, it)
    }
    val jars = layer.incoming.artifactView { componentFilter { it is ModuleComponentIdentifier } }.files
    val listing = layout.buildDirectory.file("libs/layers/$name.txt")
    tasks.register("${name}DockerLayer") {
        description = "Lists the libraries the $name layer of the container image holds."
        inputs.files(jars)
        outputs.file(listing)
        doLast {
            listing.get().asFile.writeText(jars.files.map { it.name }.sorted().joinToString("\n", postfix = "\n"))
        }
    }
}

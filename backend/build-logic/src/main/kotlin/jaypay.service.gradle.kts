plugins {
    id("jaypay.java")
    id("jaypay.slf4j-only")
    id("org.springframework.boot")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

dockerLayer(
    "service",
    implementation = listOf(
        project(":libs:common"),
        lib("spring-boot-starter-webmvc"),
        lib("spring-boot-starter-actuator"),
        lib("springdoc-openapi-starter-webmvc-ui"),
    ),
    runtimeOnly = listOf(lib("micrometer-registry-prometheus"), lib("axon-tracing-opentelemetry")),
)

sourceSets {
    test {
        java.srcDir(rootProject.layout.projectDirectory.dir("build-logic/src/service-test/java"))
    }
}

dependencies {
    testImplementation(lib("spring-boot-starter-webmvc-test"))
    testImplementation(lib("opentelemetry-sdk-testing"))
}

tasks.jar {
    enabled = false
}

tasks.bootJar {
    archiveFileName = "app.jar"
}

tasks.register<Exec>("dockerBuild") {
    group = "build"
    description = "Builds the jaypay/${project.name} container image from backend/Dockerfile."
    dependsOn(tasks.bootJar, tasks.matching { it.name.endsWith("DockerLayer") })
    val context = tasks.bootJar.flatMap { it.destinationDirectory }
    val dockerfile = rootProject.file("Dockerfile")
    val image = "jaypay/${project.name}"
    inputs.file(dockerfile)
    doFirst {
        commandLine("docker", "build", "--file", dockerfile.path, "--tag", image, context.get().asFile.path)
    }
}

plugins {
    id("jaypay.java")
    id("org.springframework.boot")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

dependencies {
    implementation(project(":libs:common"))
    implementation(lib("spring-boot-starter-webmvc"))
    implementation(lib("spring-boot-starter-actuator"))
    implementation(lib("springdoc-openapi-starter-webmvc-ui"))
    testImplementation(lib("spring-boot-starter-webmvc-test"))
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
    dependsOn(tasks.bootJar)
    workingDir = rootDir
    commandLine("docker", "build", "--build-arg", "SERVICE=${project.name}", "--tag", "jaypay/${project.name}", ".")
}

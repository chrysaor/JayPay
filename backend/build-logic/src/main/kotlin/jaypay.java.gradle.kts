plugins {
    `java-library`
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

group = "com.jaypay"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.findVersion("java").get().requiredVersion)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    val springBoot = platform(lib("spring-boot-dependencies"))
    implementation(springBoot)
    implementation(platform(lib("axon-bom")))
    annotationProcessor(springBoot)
    testAnnotationProcessor(springBoot)

    compileOnly(lib("lombok"))
    annotationProcessor(lib("lombok"))
    testCompileOnly(lib("lombok"))
    testAnnotationProcessor(lib("lombok"))

    testImplementation(lib("spring-boot-starter-test"))
    testRuntimeOnly(lib("junit-platform-launcher"))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

val dockerContextHost: String? = if (System.getenv("DOCKER_HOST") != null) null else runCatching {
    providers.exec {
        commandLine("docker", "context", "inspect", "--format", "{{.Endpoints.docker.Host}}")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrNull()

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    if (dockerContextHost != null && dockerContextHost.startsWith("unix://") && dockerContextHost != "unix:///var/run/docker.sock") {
        environment("DOCKER_HOST", dockerContextHost)
        environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    }
}

tasks.register("resolveAndLockAll") {
    group = "help"
    description = "Resolves every configuration so --write-locks records it in gradle.lockfile."
    notCompatibleWithConfigurationCache("Resolves configurations at execution time")
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) { "Run with --write-locks" }
    }
    doLast {
        configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
    }
}

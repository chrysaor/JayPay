plugins {
    id("jaypay.service")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

dockerLayer("jpa", implementation = listOf(lib("spring-boot-starter-data-jpa")), runtimeOnly = listOf(lib("postgresql")))

dependencies {
    testImplementation(lib("spring-boot-starter-data-jpa-test"))
    testRuntimeOnly(lib("testcontainers-postgresql"))
}

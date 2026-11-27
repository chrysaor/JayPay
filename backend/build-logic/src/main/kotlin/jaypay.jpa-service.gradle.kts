plugins {
    id("jaypay.service")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

dockerLayer("jpa", implementation = listOf(lib("spring-boot-starter-data-jpa")), runtimeOnly = listOf(lib("postgresql")))

sourceSets {
    test {
        java.srcDir(rootProject.layout.projectDirectory.dir("build-logic/src/jpa-service-test/java"))
    }
}

dependencies {
    testImplementation(lib("spring-boot-starter-data-jpa-test"))
    testRuntimeOnly(lib("testcontainers-postgresql"))
}

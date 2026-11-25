plugins {
    id("jaypay.service")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

dependencies {
    implementation(lib("spring-boot-starter-data-jpa"))
    runtimeOnly(lib("postgresql"))
    testImplementation(lib("spring-boot-starter-data-jpa-test"))
    testRuntimeOnly(lib("testcontainers-postgresql"))
}

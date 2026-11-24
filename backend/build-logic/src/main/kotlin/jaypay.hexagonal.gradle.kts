plugins {
    id("jaypay.java")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

sourceSets {
    test {
        java.srcDir(rootProject.layout.projectDirectory.dir("build-logic/src/hexagonal/java"))
    }
}

dependencies {
    testImplementation(lib("archunit"))
}

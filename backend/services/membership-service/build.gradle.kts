plugins {
    id("jaypay.jpa-service")
    id("jaypay.hexagonal")
}

dependencies {
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)
    implementation(libs.spring.vault.core)
}

plugins {
    id("jaypay.jpa-service")
    id("jaypay.hexagonal")
}

dependencies {
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
}

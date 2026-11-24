plugins {
    id("jaypay.jpa-service")
}

dependencies {
    implementation(libs.spring.boot.starter.batch.jdbc)
}

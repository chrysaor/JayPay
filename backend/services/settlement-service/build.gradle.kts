plugins {
    id("jaypay.jpa-service")
    id("jaypay.hexagonal")
}

dependencies {
    implementation(libs.spring.boot.starter.batch.jdbc)
    testImplementation(testFixtures(project(":libs:common")))
}

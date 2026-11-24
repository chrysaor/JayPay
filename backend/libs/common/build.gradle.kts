plugins {
    id("jaypay.java")
    id("jaypay.slf4j-only")
    `java-test-fixtures`
}

dependencies {
    api(libs.spring.boot.starter.validation)
    api(libs.spring.boot.starter.aspectj)
    api(libs.spring.boot.starter.kafka)
    api(libs.jackson2.databind)
    api(libs.jackson2.datatype.jsr310)
    api(libs.axon.spring.boot.starter)

    testFixturesImplementation(platform(libs.spring.boot.dependencies))
    testFixturesImplementation(platform(libs.axon.bom))
    testFixturesApi(libs.testcontainers.kafka)
}

plugins {
    id("jaypay.java")
    id("jaypay.slf4j-only")
    `java-test-fixtures`
}

dependencies {
    api(libs.spring.boot.starter.aspectj)
    api(libs.spring.boot.starter.kafka)
    api(libs.spring.boot.starter.opentelemetry)
    api(libs.jackson2.databind)
    api(libs.jackson2.datatype.jsr310)
    api(libs.axon.spring.boot.starter)
    compileOnly(libs.spring.boot.starter.data.jpa)
    implementation(libs.micrometer.java11)
    implementation(libs.opentelemetry.logback.appender)

    testFixturesImplementation(platform(libs.spring.boot.dependencies))
    testFixturesImplementation(platform(libs.axon.bom))
    testFixturesApi(libs.testcontainers.kafka)
}

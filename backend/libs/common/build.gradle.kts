plugins {
    id("jaypay.java")
}

dependencies {
    api(libs.spring.boot.starter.validation)
    api(libs.spring.boot.starter.aspectj)
    api(libs.kafka.clients)
    api(libs.jackson2.databind)
    api(libs.jackson2.datatype.jsr310)
    api(libs.axon.spring.boot.starter)
}

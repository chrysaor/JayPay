plugins {
    id("jaypay.jpa-service")
}

dependencies {
    testImplementation(testFixtures(project(":libs:common")))
}

plugins {
    id("jaypay.jpa-service")
    id("jaypay.hexagonal")
}

dependencies {
    testImplementation(testFixtures(project(":libs:common")))
}

plugins {
    id("jaypay.service")
}

dependencies {
    testImplementation(testFixtures(project(":libs:common")))
}

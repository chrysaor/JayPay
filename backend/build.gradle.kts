plugins {
    base
    `jacoco-report-aggregation`
    id("jaypay.locking")
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

dependencies {
    subprojects.filter { it.buildFile.exists() }.forEach { jacocoAggregation(project(it.path)) }
}

reporting {
    reports {
        register<JacocoCoverageReport>("testCodeCoverageReport") {
            testSuiteName = "test"
        }
    }
}

tasks.named<JacocoReport>("testCodeCoverageReport") {
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.check {
    dependsOn("testCodeCoverageReport")
}

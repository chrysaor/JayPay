pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "jaypay"

include(
    ":libs:common",
    ":services:banking-service",
    ":services:event-flow-service",
    ":services:logging-consumer",
    ":services:membership-service",
    ":services:money-aggregation-service",
    ":services:money-query-service",
    ":services:money-service",
    ":services:payment-service",
    ":services:remittance-service",
    ":services:settlement-service",
    ":tools:utility",
)

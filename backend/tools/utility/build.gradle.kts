plugins {
    id("jaypay.java")
}

dependencies {
    implementation(libs.json)
    runtimeOnly(libs.postgresql)
}

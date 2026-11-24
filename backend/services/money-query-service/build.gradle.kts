plugins {
    id("jaypay.jpa-service")
    id("jaypay.hexagonal")
}

dependencies {
    implementation(platform(libs.awssdk.bom))
    implementation(libs.awssdk.dynamodb)
}

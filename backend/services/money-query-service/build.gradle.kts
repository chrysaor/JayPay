plugins {
    id("jaypay.jpa-service")
}

dependencies {
    implementation(platform(libs.awssdk.bom))
    implementation(libs.awssdk.dynamodb)
}

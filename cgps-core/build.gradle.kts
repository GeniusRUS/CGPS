dokka {
    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("javadoc"))
    }
}

android {
    namespace = "com.genius.coroutinesgps"
}

dependencies {
    implementation(libs.core)
    implementation(libs.annotation)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlin)
    implementation(libs.junit.ktx)
    implementation(libs.rules)
    dokkaPlugin(libs.android.documentation.plugin)

    testImplementation(libs.junit.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.test.core)
}
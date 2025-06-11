plugins {
    id("com.huawei.agconnect")
}

dokka {
    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("javadoc"))
    }
}

android {
    namespace = "com.genius.cgps.huawei"
}

dependencies {
    api(project(":cgps-core"))
    implementation(libs.core.ktx)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.rules)

    implementation(libs.huawei.location)
    implementation(libs.kotlinx.coroutines.android)
    dokkaPlugin(libs.android.documentation.plugin)
}
plugins {
    alias(libs.plugins.android.library)
}

dokka {
    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("javadoc"))
    }
}

android {
    namespace = "com.genius.cgps.google"
}

dependencies {
    api(project(":cgps-core"))
    implementation(libs.core.ktx)
    dokkaPlugin(libs.android.documentation.plugin)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.rules)

    implementation(libs.google.location)
    implementation(libs.kotlinx.coroutines.play.services)
}
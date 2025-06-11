dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":cgps-google"))
    implementation(project(":cgps-huawei"))
    implementation(libs.kotlin)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.material)
    testImplementation(libs.junit.junit)
    androidTestImplementation(libs.runner)
    androidTestImplementation(libs.espresso.core)
}

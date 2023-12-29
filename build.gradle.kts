plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.dokka) apply false
    alias(libs.plugins.maven.publish) apply false
    // https://developer.huawei.com/consumer/en/doc/development/AppGallery-connect-Guides/agc-appmessage-sdkchangenotes-android-0000001072373122
    id("com.huawei.agconnect.agcp") version "1.9.1.302" apply false
}

task<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")
}
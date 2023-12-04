plugins {
    id("com.android.application") version "8.2.0" apply false
    id("com.android.library") version "8.2.0" apply false
    kotlin("android") version "1.9.21" apply false
    id("org.jetbrains.dokka") version "1.9.10" apply false
    id("com.vanniktech.maven.publish") version "0.25.3" apply false
    // https://developer.huawei.com/consumer/en/doc/development/AppGallery-connect-Guides/agc-appmessage-sdkchangenotes-android-0000001072373122
    id("com.huawei.agconnect.agcp") version "1.9.1.301" apply false
}

task<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")
}

extra.apply {
    set("coroutineVer", "1.7.3")
    set("coreVer", "1.12.0")
}
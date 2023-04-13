plugins {
    id("com.android.application") version "7.4.2" apply false
    id("com.android.library") version "7.4.2" apply false
    id("org.jetbrains.kotlin.android") version "1.8.10" apply false
    id("org.jetbrains.dokka") version "1.8.10" apply false
    id("com.vanniktech.maven.publish") version "0.25.1" apply false
    id("com.huawei.agconnect.agcp") version "1.8.1.300" apply false
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")
}

extra.apply {
    set("coroutineVer", "1.6.4")
}
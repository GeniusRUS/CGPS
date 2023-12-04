plugins {
    id("com.android.library")
    id("com.huawei.agconnect")
    id("com.vanniktech.maven.publish")
    kotlin("android")
}

tasks.dokkaJavadoc.configure {
    outputDirectory.set(layout.buildDirectory.dir("javadoc"))
}

mavenPublishing {
    signAllPublications()
    publishToMavenCentral()
}

android {
    namespace = "com.genius.cgps.huawei"
    compileSdk = 34
    defaultConfig {
        minSdk = 19
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
    packaging {
        resources.excludes += "DebugProbesKt.bin"
    }
}

val coroutineVer: String by project
val coreVer: String by project

dependencies {
    api(project(":cgps-core"))
    implementation("androidx.core:core-ktx:$coreVer")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:rules:1.5.0")

    // https://developer.huawei.com/consumer/de/doc/development/HMSCore-Guides/version-change-history-0000001050986155
    implementation("com.huawei.hms:location:6.12.0.300")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutineVer")
}
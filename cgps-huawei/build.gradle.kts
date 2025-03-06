plugins {
    alias(libs.plugins.android.library)
    id("com.huawei.agconnect")
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.kotlin.android)
}

dokka {
    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("javadoc"))
    }
}

mavenPublishing {
    signAllPublications()
    publishToMavenCentral()
}

android {
    namespace = "com.genius.cgps.huawei"
    compileSdk = 35
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
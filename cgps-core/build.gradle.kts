import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("com.android.library")
    kotlin("android")
    id("com.vanniktech.maven.publish")
}

tasks.dokkaJavadoc.configure {
    outputDirectory.set(layout.buildDirectory.dir("javadoc"))
}

mavenPublishing {
    signAllPublications()
    publishToMavenCentral()
}

android {
    namespace = "com.genius.coroutinesgps"
    compileSdk = 34
    defaultConfig {
        minSdk = 16
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
    buildFeatures {
        buildConfig = false
    }
    packaging {
        resources.excludes += "DebugProbesKt.bin"
    }
}

val coroutineVer: String by project
val coreVer: String by project

dependencies {
    implementation("androidx.core:core:$coreVer")
    implementation("androidx.annotation:annotation:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutineVer")
    implementation(kotlin("stdlib", KotlinCompilerVersion.VERSION))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("androidx.test:core:1.5.0")
}
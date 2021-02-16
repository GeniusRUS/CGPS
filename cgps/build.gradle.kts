import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.kotlin.config.KotlinCompilerVersion

buildscript {
    repositories {
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }
    dependencies {
        classpath("com.vanniktech:gradle-maven-publish-plugin:0.14.2")
    }
}

apply(plugin = "com.vanniktech.maven.publish")

plugins {
    id("com.android.library")
    kotlin("android")
    id("org.jetbrains.dokka")
}

tasks.dokkaJavadoc.configure {
    outputDirectory.set(buildDir.resolve("javadoc"))
}

android {
    compileSdkVersion(30)
    defaultConfig {
        minSdkVersion(16)
        targetSdkVersion(30)
        versionCode = 1
        versionName = gradleLocalProperties(rootDir).getProperty("VERSION_NAME")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    lintOptions {
        isAbortOnError = false
    }

    buildFeatures {
        buildConfig = false
    }
}

val verCoroutinesStuff = "1.4.2"

dependencies {
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$verCoroutinesStuff")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$verCoroutinesStuff")
    implementation(kotlin("stdlib-jdk7", KotlinCompilerVersion.VERSION))

    implementation("com.google.android.gms:play-services-location:17.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:3.3.3")
    testImplementation("androidx.test:core:1.3.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.2")
    androidTestImplementation("androidx.test:runner:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")
    androidTestImplementation("androidx.test:rules:1.3.0")
}
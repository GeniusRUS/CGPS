import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.config.KotlinCompilerVersion

buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:${rootProject.extra.get("dokka_version") as String}")
    }
}

plugins {
    id("com.jfrog.bintray")
    id("com.github.dcendents.android-maven")
    id("org.jetbrains.dokka") version "0.9.18"
    id("com.android.library")
    kotlin("android")
    kotlin("android.extensions")
}

tasks.withType<DokkaTask> {
    outputFormat = "html"
    outputDirectory = "$buildDir/javadoc"
}

extra.apply{
    set("bintrayRepo", "CGPS")
    set("bintrayName", "com.geniusrus.cgps")
    set("libraryName", "cgps")
    set("publishedGroupId", "com.geniusrus.cgps")
    set("artifact", "cgps")
    set("libraryVersion", "1.6.0")
    set("libraryDescription", "Android location library on coroutines")
    set("siteUrl", "https://github.com/GeniusRUS/CGPS")
    set("gitUrl", "https://github.com/GeniusRUS/CGPS.git")
    set("developerId", "GeniusRUS")
    set("developerName", "Viktor Likhanov")
    set("developerEmail", "Gen1usRUS@yandex.ru")
    set("licenseName", "The Apache Software License, Version 2.0")
    set("licenseUrl", "http://www.apache.org/licenses/LICENSE-2.0.txt")
    set("allLicenses", arrayOf("Apache-2.0"))
}

android {
    compileSdkVersion(29)
    defaultConfig {
        minSdkVersion(16)
        targetSdkVersion(29)
        versionCode = 1
        versionName = extra.get("libraryVersion") as String
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    lintOptions {
        isAbortOnError = false
    }
}

val verCoroutinesStuff = "1.3.2"

dependencies {
    implementation("androidx.appcompat:appcompat:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$verCoroutinesStuff")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$verCoroutinesStuff")
    implementation(kotlin("stdlib-jdk7", KotlinCompilerVersion.VERSION))

    implementation("com.google.android.gms:play-services-location:17.0.0")

    testImplementation("junit:junit:4.12")
    testImplementation("org.mockito:mockito-core:3.1.0")
    testImplementation("androidx.test:core:1.2.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.1")
    androidTestImplementation("androidx.test:runner:1.2.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.2.0")
    androidTestImplementation("androidx.test:rules:1.2.0")
}

/**
 * Создаёт JAR-файл библиотеки в cgps/libs/jar
 */
task<Copy>("createJarRelease") {
    from("$buildDir/intermediates/intermediate-jars/release/")
    into("libs/jar")
    include("classes.jar")
    rename("classes.jar", "CGPS.jar")
    exclude("**/BuildConfig.class")
    exclude("**/R.class")
    exclude("**/R$*.class")
}

if (project.rootProject.file("local.properties").exists()) {
    apply("https://raw.githubusercontent.com/nuuneoi/JCenter/master/installv1.gradle")
    apply("https://raw.githubusercontent.com/nuuneoi/JCenter/master/bintrayv1.gradle")
}

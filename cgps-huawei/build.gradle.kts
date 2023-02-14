plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.huawei.agconnect")
    id("org.jetbrains.dokka")
    id("com.vanniktech.maven.publish")
}

tasks.dokkaJavadoc.configure {
    outputDirectory.set(buildDir.resolve("javadoc"))
}

mavenPublishing {
    signAllPublications()
    publishToMavenCentral()
}

android {
    namespace = "com.genius.cgps.huawei"
    compileSdk = 33
    defaultConfig {
        minSdk = 16
        targetSdk = 33
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

val coroutineVer: String by project

dependencies {
    api(project(":cgps-core"))
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:rules:1.5.0")

    implementation("com.huawei.hms:location:6.4.0.300")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutineVer")
}
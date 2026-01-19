import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.dokka) apply false
    alias(libs.plugins.maven.publish) apply false
    // https://developer.huawei.com/consumer/en/doc/development/AppGallery-connect-Guides/agc-appmessage-sdkchangenotes-android-0000001072373122
    id("com.huawei.agconnect.agcp") version "1.9.1.304" apply false
}

subprojects {
    val isApp = project.name == "app"

    if (isApp) {
        pluginManager.apply(rootProject.libs.plugins.android.application.get().pluginId)
    } else {
        pluginManager.apply(rootProject.libs.plugins.android.library.get().pluginId)
    }

    pluginManager.apply(rootProject.libs.plugins.kotlin.dokka.get().pluginId)

    if (project.name.contains("cgps-")) {
        pluginManager.apply(rootProject.libs.plugins.maven.publish.get().pluginId)
    }

    plugins.withType<AppPlugin> {
        configure<ApplicationExtension> {
            namespace = "com.genius.example"
            compileSdk = rootProject.libs.versions.sdk.compile.get().toInt()
            defaultConfig {
                applicationId = "com.genius.cgps"
                minSdk = 23
                targetSdk = rootProject.libs.versions.sdk.target.get().toInt()
                versionCode = 1
                versionName = "1.0"
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                multiDexEnabled = true
            }
            buildTypes {
                release {
                    isMinifyEnabled = false
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                }
            }
            compileOptions {
                sourceCompatibility = JavaVersion.toVersion(rootProject.libs.versions.jdk.get())
                targetCompatibility = JavaVersion.toVersion(rootProject.libs.versions.jdk.get())
            }
        }
    }
    plugins.withType<LibraryPlugin>().configureEach {
        configure<LibraryExtension> {
            compileSdk = rootProject.libs.versions.sdk.compile.get().toInt()

            defaultConfig {
                minSdk = if (project.name == "cgps-huawei") {
                    rootProject.libs.versions.sdk.min.huawei.get().toInt()
                } else {
                    rootProject.libs.versions.sdk.min.common.get().toInt()
                }
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            buildTypes {
                debug {
                    isMinifyEnabled = false
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                }

                release {
                    isMinifyEnabled = false
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                    packaging.resources.excludes += "DebugProbesKt.bin"
                }

                create("develop")
            }
            compileOptions {
                sourceCompatibility = JavaVersion.toVersion(rootProject.libs.versions.jdk.get())
                targetCompatibility = JavaVersion.toVersion(rootProject.libs.versions.jdk.get())
            }
        }
    }
    plugins.withType<KotlinAndroidPluginWrapper> {
        tasks.withType<KotlinJvmCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(libs.versions.jdk.get()))
            }
        }
    }
    plugins.withType<MavenPublishPlugin> {
        configure<MavenPublishBaseExtension> {
            signAllPublications()
            publishToMavenCentral()
        }
    }
}
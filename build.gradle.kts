import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.dokka) apply false
    alias(libs.plugins.maven.publish) apply false
    // https://developer.huawei.com/consumer/en/doc/development/AppGallery-connect-Guides/agc-appmessage-sdkchangenotes-android-0000001072373122
    id("com.huawei.agconnect.agcp") version "1.9.1.304" apply false
}

subprojects {
    apply {
        plugin(rootProject.libs.plugins.kotlin.android.get().pluginId)
        plugin(rootProject.libs.plugins.kotlin.dokka.get().pluginId)
    }

    if (project.name.contains("cgps-")) {
        apply {
            plugin(rootProject.libs.plugins.maven.publish.get().pluginId)
        }
    }

    plugins.withType<LibraryPlugin>().configureEach {
        configure<LibraryExtension> {
            compileSdk = libs.versions.sdk.compile.get().toInt()

            defaultConfig {
                minSdk = if (project.name == "cgps-huawei") {
                    libs.versions.sdk.min.huawei.get().toInt()
                } else {
                    libs.versions.sdk.min.common.get().toInt()
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
            }
            compileOptions {
                sourceCompatibility = JavaVersion.toVersion(libs.versions.jdk.get())
                targetCompatibility = JavaVersion.toVersion(libs.versions.jdk.get())
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
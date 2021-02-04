import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
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
    id("maven-publish")
    id("com.android.library")
    kotlin("android")
    id("org.jetbrains.dokka") version "1.4.20"
}

android {
    compileSdkVersion(30)
    defaultConfig {
        minSdkVersion(16)
        targetSdkVersion(30)
        versionCode = 1
        versionName = "1.8.0"
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

    testImplementation("junit:junit:4.13.1")
    testImplementation("org.mockito:mockito-core:3.3.3")
    testImplementation("androidx.test:core:1.3.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.2")
    androidTestImplementation("androidx.test:runner:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")
    androidTestImplementation("androidx.test:rules:1.3.0")
}

group = "com.geniusrus.cgps"
version = android.defaultConfig.versionName.toString()

tasks {
    dokkaJavadoc.configure {
        outputDirectory.set(buildDir.resolve("javadoc"))
        dokkaSourceSets {
            configureEach {
                sourceLink {
                    localDirectory.set(file("src/main/java"))
                    remoteUrl.set(uri("https://github.com/GeniusRUS/CGPS/tree/master/cgps/src/main/java").toURL())
                    remoteLineSuffix.set("#L")
                }
            }
        }
    }

    register("androidJavadocJar", Jar::class) {
        archiveClassifier.set("javadoc")
        from("$buildDir/javadoc")
        dependsOn(dokkaJavadoc)
    }

    register("androidSourcesJar", Jar::class) {
        archiveClassifier.set("sources")
        from(android.sourceSets.getByName("main").java.srcDirs)
    }
}

publishing {
    publications {
        register<MavenPublication>("CGPSLibrary") {
            artifactId = "cgps"

            afterEvaluate { artifact(tasks.getByName("bundleReleaseAar")) }
            artifact(tasks.getByName("androidJavadocJar"))
            artifact(tasks.getByName("androidSourcesJar"))

            pom {
                name.set("CGPS")
                description.set("Android location library on coroutines")
                url.set("https://github.com/GeniusRUS/CGPS")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("GeniusRUS")
                        name.set("Viktor Likhanov")
                        email.set("Gen1usRUS@yandex.ru")
                    }
                }
                scm {
                    connection.set("git://github.com/GeniusRUS/CGPS.git")
                    developerConnection.set("git://github.com/GeniusRUS/CGPS.git")
                    url.set("https://github.com/GeniusRUS/CGPS")
                }

                // Saving external dependencies list into .pom-file
                withXml {
                    fun groovy.util.Node.addDependency(dependency: Dependency, scope: String) {
                        appendNode("dependency").apply {
                            appendNode("groupId", dependency.group)
                            appendNode("artifactId", dependency.name)
                            appendNode("version", dependency.version)
                            appendNode("scope", scope)
                        }
                    }

                    asNode().appendNode("dependencies").let { dependencies ->
                        // List all "api" dependencies as "compile" dependencies
                        configurations.api.get().allDependencies.forEach {
                            dependencies.addDependency(it, "compile")
                        }
                        // List all "implementation" dependencies as "runtime" dependencies
                        configurations.implementation.get().allDependencies.forEach {
                            dependencies.addDependency(it, "runtime")
                        }
                    }
                }
            }
        }
    }

    repositories {
        maven {
            name = "bintray"
            credentials {
                username = gradleLocalProperties(rootDir).getProperty("bintray.user")
                password = gradleLocalProperties(rootDir).getProperty("bintray.apikey")
            }
            url = uri("https://api.bintray.com/maven/geniusrus/CGPS/$group/;publish=1")
        }
    }
}

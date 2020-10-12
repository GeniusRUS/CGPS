// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    val kotlinVersion: String by extra { "1.4.10" }
    extra.apply{
        set("dokka_version", kotlinVersion)
    }
    repositories {
        google()
        jcenter()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:4.1.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")

        classpath("com.jfrog.bintray.gradle:gradle-bintray-plugin:1.8.4")
        classpath("com.github.dcendents:android-maven-gradle-plugin:2.1")
    }
}

allprojects {
    repositories {
        google()
        jcenter()
    }
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}

// решает проблему ошибки генерации javadoc'ов при gradlew install
subprojects {
    tasks.withType<Javadoc>().all { enabled = false }
}
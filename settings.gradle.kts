pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven { setUrl("https://developer.huawei.com/repo/") }
    }
    plugins {}
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.huawei.agconnect") {
                if (requested.id.id == "com.huawei.agconnect.agcp") {
                    useModule("com.huawei.agconnect:agcp:${requested.version}")
                }
            }
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Warning: this repository is going to shut down soon
        maven { setUrl("https://developer.huawei.com/repo/") }
    }
}
include(":app", ":cgps-core", ":cgps-huawei", ":cgps-google")

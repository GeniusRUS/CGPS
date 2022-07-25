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
                    useModule("com.huawei.agconnect:agcp:1.7.0.300")
                }
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        jcenter() // Warning: this repository is going to shut down soon
        maven { setUrl("https://developer.huawei.com/repo/") }
    }
}
include(":app", ":cgps-core", ":cgps-huawei", ":cgps-google")

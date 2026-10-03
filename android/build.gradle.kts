plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

group = "com.achieve"
version = "0.1.0"

android {
    namespace = "com.achieve.unitybridge"
    compileSdk = 36
    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    publishing { singleVariant("release") { withSourcesJar() } }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    testImplementation("junit:junit:4.13.2")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "android-unity-bridge"
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("Android Unity Bridge")
                description.set("Launch Unity from Android and exchange session-scoped messages.")
            }
        }
    }
    repositories {
        maven { name = "localBuild"; url = uri(layout.buildDirectory.dir("repository")) }
    }
}

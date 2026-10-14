import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.protobuf")
}

android {
    namespace = "com.dm20remote"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.dm20remote"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        // ConsoleClient logs through android.util.Log, which is not available
        // on the JVM; return defaults so unit tests can exercise the class.
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.36.2"
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-service:2.10.0")
    implementation("androidx.activity:activity-compose:1.12.4")

    val composeUi = "1.11.4"
    implementation("androidx.compose.ui:ui:$composeUi")
    implementation("androidx.compose.ui:ui-graphics:$composeUi")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeUi")
    implementation("androidx.compose.material3:material3:1.4.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("com.google.protobuf:protobuf-javalite:4.36.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling:$composeUi")
}

configurations.all {
    resolutionStrategy {
        force(
            "androidx.core:core-ktx:1.18.0",
            "androidx.lifecycle:lifecycle-runtime-ktx:2.10.0",
            "androidx.lifecycle:lifecycle-runtime-compose:2.10.0",
            "androidx.lifecycle:lifecycle-service:2.10.0",
            "androidx.activity:activity-compose:1.12.4",
            "androidx.compose.ui:ui:1.11.4",
            "androidx.compose.ui:ui-android:1.11.4",
            "androidx.compose.ui:ui-graphics:1.11.4",
            "androidx.compose.ui:ui-graphics-android:1.11.4",
            "androidx.compose.ui:ui-tooling-preview:1.11.4",
            "androidx.compose.ui:ui-tooling-preview-android:1.11.4",
            "androidx.compose.material3:material3:1.4.0",
            "androidx.compose.material3:material3-android:1.4.0",
        )
    }
}

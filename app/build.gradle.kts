plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.sbf.lightspeed"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sbf.lightspeed"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "1.4.0"
        manifestPlaceholders["appName"] = "Lightspeed"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".nightly"
            versionNameSuffix = "-NIGHTLY"
            manifestPlaceholders["appName"] = "Lightspeed Nightly"
        }
        release {
            isMinifyEnabled = false
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            manifestPlaceholders["appName"] = "Lightspeed"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }

    applicationVariants.all {
        outputs.all {
            val f = (this as? com.android.build.gradle.internal.api.ApkVariantOutputImpl)?.outputFile
            println(">>> VARIANT OUTPUT PATH: ${f?.absolutePath}")
        }
    }
}

afterEvaluate {
    tasks.named("packageDebug").configure {
        doLast {
            val intermediateDir = file("build/intermediates/apk/debug/packageDebug")
            val outputDir = file("build/outputs/apk/debug").apply { mkdirs() }
            val targetApk = File(outputDir, "app-debug.apk")
            if (intermediateDir.exists()) {
                intermediateDir.walk().filter { it.isFile && it.extension == "apk" }.forEach { foundApk ->
                    foundApk.copyTo(targetApk, overwrite = true)
                    println(">>> SUCCESSFULLY COPIED APK TO: ${targetApk.absolutePath} (${targetApk.length()} bytes)")
                }
            }
        }
    }
}



dependencies {
    // Elevated system IPC (Shizuku & compatible forks)
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}


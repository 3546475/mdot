import java.util.Properties

// 绛惧悕淇℃伅锛坘eystore/keystore.properties锛屼笉鍏ュ簱锛涚己澶辨椂 release 涓嶇鍚嶏級
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.mdot.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mdot.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 24
        versionName = "0.5.7"
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            ndk {
                // x86_64 渚涙ā鎷熷櫒锛況elease 浠呭嚭 arm 鍙屾灦鏋勶紙02 鏂囨。 搂7锛?
                abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
            }
        }
        release {
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystoreProps.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else null
        }
    }
    // ABI 鎷嗗垎锛歳elease 鍑?v7a/v8a 鍗曟灦鏋勫寘 + universal锛堝弻鏋舵瀯鍚堝苟锛夊寘锛?
    // 鍙戝竷鍛藉悕绾﹀畾锛歁DOT-RELEASE-<鐗堟湰鍙?-v8a.apk / -v7a.apk锛堝崟鏋舵瀯锛夈€丮DOT-RELEASE-<鐗堟湰鍙?.apk锛堝弻鏋舵瀯锛?
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
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
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)

    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    // 澶村儚瑁佸壀锛圕anHub Android-Image-Cropper锛屾垚鐔?uCrop 鍒嗘敮锛涢渶涓哄叾 Activity 琛?AppCompat 涓婚锛?
    implementation("com.vanniktech:android-image-cropper:4.7.0")

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.junit)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
}

import java.util.Properties

// 签名信息（keystore/keystore.properties，不入库；缺失时 release 不签名）
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// B1-05：文件存在但键残缺时报明确的缺失键名，而非后续 as String 的 ClassCastException
if (keystoreProps.isNotEmpty()) {
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword").forEach { k ->
        require(keystoreProps.getProperty(k) != null) {
            "keystore/keystore.properties 缺少键：$k（四项 storeFile/storePassword/keyAlias/keyPassword 必须齐全）"
        }
    }
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
        versionCode = 45
        versionName = "0.6.17"
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
                // x86_64 供模拟器；release 仅出 arm 双架构（02 文档 §7）
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
    // ABI 拆分：release 出 v7a/v8a 单架构包 + universal（双架构合并）包
    // 发布命名约定：MDOT-RELEASE-<版本号>-v8a.apk / -v7a.apk（单架构）、MDOT-RELEASE-<版本号>-dual.apk（双架构）
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
    // B1-09：仅保留中英资源——Compose/M3/AppCompat/cropper 等库携带数十种语言，全量打入无谓增大
    androidResources {
        localeFilters += listOf("zh", "en")
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

    // 头像裁剪（CanHub Android-Image-Cropper，成熟 uCrop 分支；需为其 Activity 补 AppCompat 主题）
    implementation("com.vanniktech:android-image-cropper:4.7.0")

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.junit)
}

/**
 * Robolectric 运行期要下载 `android-all-instrumented` jar（默认源 `https://repo1.maven.org/maven2`）。
 *
 * ⚠️ **Gradle 命令行 `-D` 只作用于 Gradle 进程，不会传给 fork 出的测试 JVM**——
 * 在 .cnb.yml 里写 `./gradlew ... -Drobolectric.dependency.repo.url=...` 是无效的。
 * 后果：CI（国内 runner）上 Robolectric 仍直连 Maven Central，偶发
 * `MavenArtifactFetcher` → `HttpURLConnection` IOException，挂掉最先跑的 RecordDaoTest 2 例，
 * 单测门禁随机失败（v0.6.17 事故，见 docs/11 035）。必须在此用 `systemProperty` 显式透传。
 *
 * 仓库选择沿用 settings.gradle.kts 的约定：`CI` 非空（GitHub Actions 海外 runner）用官方源，
 * 否则（本地开发 / CNB 置空 CI）用阿里云镜像（已核实含本版本 jar）。
 */
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    val onCi = System.getenv("CI")?.isNotBlank() == true
    systemProperty(
        "robolectric.dependency.repo.url",
        if (onCi) "https://repo1.maven.org/maven2" else "https://maven.aliyun.com/repository/public",
    )
}

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
 * Robolectric 运行期依赖（`android-all-instrumented` jar，单个 ~200MB）**改为构建期解析 + 离线目录**。
 *
 * 背景（v0.6.17 单测门禁事故，见 docs/11 035）：Robolectric 默认在**测试运行期**从
 * Maven Central 下载这些 jar（`MavenArtifactFetcher`），国内 CI 极不稳 →
 * `HttpURLConnection` IOException，挂掉最先跑的 RecordDaoTest，单测门禁随机失败。
 * 仅靠 `robolectric.dependency.repo.url` 指镜像在 CNB 容器上仍不够（大文件/路径原因，实测仍失败）。
 *
 * 做法：用 Gradle 自己的仓库配置（带镜像/重试/缓存）在**构建期**把这些 jar 解析下来，
 * 复制到扁平目录，再让 Robolectric 走**离线模式**只读该目录 —— **测试运行期零网络**。
 * 不把 jar 提交进仓库（避免 ~260MB 入库），Gradle 缓存（CI 已挂载）会跨构建复用。
 *
 * ⚠️ 版本名与 Robolectric / targetSdk 绑定：升 Robolectric 或 targetSdk 后需同步更新；
 *    离线模式下缺哪个 jar 会明确报出来（不会静默降级）。
 */
/**
 * ⚠️ 每个 jar **各用一个 configuration**：它们同 group:artifact，放进同一个 configuration 会被
 * Gradle 冲突解析成单一版本（实测 `5.0.2_r3-... -> 15-...`），导致 API 21 的 jar 缺失，
 * 离线模式下 `LocalDependencyResolver` 直接抛「Path is not a file」。
 */
val robolectricDeps: List<org.gradle.api.artifacts.Configuration> = listOf(
    // targetSdk 35 对应的 instrumented jar
    "robolectricAndroidAll35" to "15-robolectric-12650502-i7",
    // Robolectric 初始化 SdkCollection 时还需要最低支持版本（API 21）的 jar
    "robolectricAndroidAll21" to "5.0.2_r3-robolectric-r0-i7",
).map { (cfgName, version) ->
    configurations.create(cfgName) {
        isCanBeConsumed = false
        isCanBeResolved = true
        dependencies.add(project.dependencies.create("org.robolectric:android-all-instrumented:$version"))
    }
}

val robolectricDepsDir = layout.buildDirectory.dir("robolectric-deps")
val prepareRobolectricDeps by tasks.registering(org.gradle.api.tasks.Copy::class) {
    robolectricDeps.forEach { from(it) }
    into(robolectricDepsDir)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    dependsOn(prepareRobolectricDeps)
    // 离线：只从 prepareRobolectricDeps 拷出来的扁平目录取 jar，运行期不联网
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricDepsDir.get().asFile.absolutePath)
    // 兜底：万一离线目录机制未生效，也别退回 Maven Central（源按 settings.gradle.kts 约定切）
    val onCi = System.getenv("CI")?.isNotBlank() == true
    systemProperty(
        "robolectric.dependency.repo.url",
        if (onCi) "https://repo1.maven.org/maven2" else "https://maven.aliyun.com/repository/public",
    )
}

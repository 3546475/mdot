import java.net.URI
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
        versionCode = 46
        versionName = "0.6.18"
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
 * Robolectric 运行期依赖（`android-all-instrumented` jar，合计 ~263MB）**构建期备好 + 持久离线目录**。
 *
 * 背景（docs/11 035 修过一次、037/042 又栽一次）：Robolectric 默认在**测试运行期**从 Maven Central
 * 下载这些 jar（`MavenArtifactFetcher`，无重试）→ 国内 runner 极不稳，挂最先跑的 RecordDaoTest。
 * v0.6.17 改成「构建期解析 + 离线目录」后**新暴露一层**：解析本身要 263MB 走网络，而 runner 上
 * `CI` 非空 → settings.gradle.kts 只列了 google/mavenCentral（**没走阿里云镜像**）→ Central 对共享
 * CI IP 限流 **429 Too Many Requests** → `:app:prepareRobolectricDeps` 解析失败、单测门禁挂、CNB 半发布。
 * **换镜像治标，缓存命中才是治本。**
 *
 * 做法（双保险）：
 * 1. **持久离线目录**：jar 备在 `~/.gradle/mdot-robolectric-deps`（CI 已挂载该卷）——**不是 `app/build/`**
 *    （构建目录会被清）。目录里已备齐 → `upToDateWhen` 直接 UP-TO-DATE：**不解析 configuration、零网络**；
 * 2. **拿不到才联网**：先让 Gradle 按 settings 的镜像仓库解析；失败再**直连镜像**（阿里云 central →
 *    阿里云 public → Central，各重试 3 次）+ 大小校验，避免单点故障把门禁打死。
 *
 * 不把 jar 提交进仓库（避免 263MB 入库）。测试运行期仍走 `robolectric.offline` 只读该目录。
 * ⚠️ 版本名与 Robolectric / targetSdk 绑定：升级后需同步；离线模式缺 jar 会明确报错（不静默降级）。
 */
/**
 * ⚠️ 每个 jar **各用一个 configuration**：同 group:artifact 放同一个 configuration 会被 Gradle 冲突解析
 * 成单一版本（实测 `5.0.2_r3-… -> 15-…`），API 21 的 jar 缺失 → 离线模式 `LocalDependencyResolver`
 * 抛「Path is not a file」。
 */
val minRobolectricJarBytes = 20L * 1024 * 1024

/** (configuration 名, 版本, 目标 jar 文件名) */
val robolectricJarSpecs = listOf(
    // targetSdk 35 对应的 instrumented jar
    Triple("robolectricAndroidAll35", "15-robolectric-12650502-i7", "android-all-instrumented-15-robolectric-12650502-i7.jar"),
    // Robolectric 初始化 SdkCollection 时还需要最低支持版本（API 21）的 jar
    Triple("robolectricAndroidAll21", "5.0.2_r3-robolectric-r0-i7", "android-all-instrumented-5.0.2_r3-robolectric-r0-i7.jar"),
)
val robolectricDeps: List<org.gradle.api.artifacts.Configuration> = robolectricJarSpecs.map { (cfgName, version, _) ->
    configurations.create(cfgName) {
        isCanBeConsumed = false
        isCanBeResolved = true
        dependencies.add(project.dependencies.create("org.robolectric:android-all-instrumented:$version"))
    }
}

/** 持久离线目录（Gradle 用户目录，CI 已挂载该卷）：跨构建复用，构建目录被清也不丢 */
val robolectricDepsDir: File = File(gradle.gradleUserHomeDir, "mdot-robolectric-deps")

/** 镜像直连兜底顺序（阿里云 central 专代理 Central，排最前） */
val robolectricMirrors = listOf(
    "https://maven.aliyun.com/repository/central",
    "https://maven.aliyun.com/repository/public",
    "https://repo1.maven.org/maven2",
)

fun isReadyRobolectricJar(f: File): Boolean = f.isFile && f.length() > minRobolectricJarBytes

val prepareRobolectricDeps = tasks.register("prepareRobolectricDeps") {
    description = "把 Robolectric 的 android-all-instrumented jar 备到持久离线目录（测试运行期零网络）"
    group = "build"
    inputs.property("robolectricVersions", robolectricJarSpecs.map { it.second })
    outputs.dir(robolectricDepsDir)
    // 目录里已备齐 → 直接 UP-TO-DATE：不解析 configuration、不联网（CI 反复构建的关键）
    outputs.upToDateWhen { robolectricJarSpecs.all { (_, _, fileName) -> isReadyRobolectricJar(File(robolectricDepsDir, fileName)) } }
    doLast {
        robolectricDepsDir.mkdirs()

        fun fromGradleCache(cfgName: String, target: File): Boolean = runCatching {
            val jar = configurations.getByName(cfgName).files.first { it.name.endsWith(".jar") }
            jar.copyTo(target, overwrite = true)
            isReadyRobolectricJar(target)
        }.getOrDefault(false)

        fun fromMirrors(version: String, target: File): Boolean {
            val rel = "org/robolectric/android-all-instrumented/$version/android-all-instrumented-$version.jar"
            for (mirror in robolectricMirrors) {
                repeat(3) {
                    val ok = runCatching {
                        URI("$mirror/$rel").toURL().openStream().use { input ->
                            target.outputStream().use { input.copyTo(it) }
                        }
                        isReadyRobolectricJar(target)
                    }.getOrDefault(false)
                    if (ok) return true
                }
            }
            return false
        }

        robolectricJarSpecs.forEach { (cfgName, version, fileName) ->
            val target = File(robolectricDepsDir, fileName)
            if (isReadyRobolectricJar(target)) return@forEach
            val ok = fromGradleCache(cfgName, target) || fromMirrors(version, target)
            check(ok) { "Robolectric 运行期 jar 准备失败：$version（Gradle 解析与镜像直连均失败）" }
            logger.lifecycle("✓ Robolectric jar 就绪：$fileName（${target.length() / 1048576} MB）")
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    dependsOn(prepareRobolectricDeps)
    // 离线：只从上面备好的扁平目录取 jar，运行期零网络
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricDepsDir.absolutePath)
    // 兜底：就算离线目录机制未生效，也只用阿里云 central（别退回对 CI IP 限流的 Maven Central）
    systemProperty("robolectric.dependency.repo.url", "https://maven.aliyun.com/repository/central")
}

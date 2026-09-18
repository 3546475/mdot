// 仓库源：CI（GitHub Actions 等海外 runner）直连官方源；本地国内开发走阿里云镜像加速。
// 注意：pluginManagement {} 块由 Gradle 独立提前编译，看不到脚本顶层变量，故判断写在块内。
pluginManagement {
    val onCi = System.getenv("CI")?.isNotBlank() == true
    repositories {
        if (onCi) {
            google()
            mavenCentral()
            gradlePluginPortal()
        } else {
            // 阿里云镜像优先，官方源兜底
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    // 国内 runner 走阿里云镜像优先：本地未设 CI，或流水线**显式**置 MDOT_MIRROR=1。
    // ⚠️ 不能只看 CI 是否为空：CNB 容器里 CI 未必为空（v0.6.18 发布事故——导致 Robolectric 的
    //    263MB jar 全从 Maven Central 拉 → 429 Too Many Requests，见 docs/11 042）。
    val useMirror = System.getenv("CI").isNullOrBlank() || System.getenv("MDOT_MIRROR") == "1"
    repositories {
        if (useMirror) {
            // aliyun central 专代理 Maven Central（Robolectric 的 android-all-instrumented 走它）
            maven("https://maven.aliyun.com/repository/central")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
            google()
            mavenCentral()
        } else {
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "overtime"
include(":app")

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
    val onCi = System.getenv("CI")?.isNotBlank() == true
    repositories {
        if (onCi) {
            google()
            mavenCentral()
        } else {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "overtime"
include(":app")

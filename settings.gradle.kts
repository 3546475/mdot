// CI（GitHub Actions 等海外 runner）直连官方源；本地国内开发走阿里云镜像加速
val useOfficialRepos = System.getenv("CI")?.isNotBlank() == true

pluginManagement {
    repositories {
        if (useOfficialRepos) {
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
    repositories {
        if (useOfficialRepos) {
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

// <project_root>/build.gradle.kts
// 使用DSL插件块进行声明，这是现代Gradle的推荐写法
plugins {
    // Android应用插件，版本需与Android Studio兼容
    id("com.android.application") version "8.13.1" apply false

    // Android库插件，用于多模块开发时的公共模块
    id("com.android.library") version "8.13.1" apply false

    // Kotlin Android插件，确保Kotlin编译器的正确配置
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false

    // Google Services插件：连接Firebase与Android应用的关键桥梁
    // 版本 4.4.x 是支持最新Firebase BoM的稳定版本
    id("com.google.gms.google-services") version "4.4.4" apply false
}

// 可以在此定义构建脚本本身的依赖仓库
buildscript {
    repositories {
        google() // Google官方Maven仓库
        mavenCentral() // 中央仓库，替代了废弃的jcenter [2]
    }
}
import org.gradle.kotlin.dsl.implementation

// <project_root>/app/build.gradle.kts

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // 应用Google Services插件，解析google-services.json
    id("com.google.gms.google-services")
}

android {
    namespace = "com.mmu.mytracker"
    compileSdk = 35 // 针对Android 14编译

    defaultConfig {
        applicationId = "com.mmu.mytracker"
        minSdk = 24 // 最低支持Android 7.0
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // 启用ViewBinding，替代findViewById，提高空安全 [5]
    buildFeatures {
        viewBinding = true
    }

    // 针对Java 8特性的支持（Lambda表达式等）
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    // =======================================================
    // 1. 地图与位置服务 (核心功能)
    // =======================================================
    // 地图 SDK
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.libraries.places:places:3.3.0")
    // 定位 SDK (获取 GPS)
    implementation("com.google.android.gms:play-services-location:21.0.1")
    // 地图工具库 (用来解码路线 Polyline，非常重要)
    implementation("com.google.maps.android:android-maps-utils:3.8.0")

    // =======================================================
    // 2. Firebase 云端数据库 (实时同步)
    // =======================================================
    // 引入 BoM (Bill of Materials)，它像个管家，自动管理下面 Firebase 库的版本
    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))

    // Analytics (分析，可选)
    implementation("com.google.firebase:firebase-analytics")

    // Realtime Database (注意：在这个新版本 BoM 中，不需要写 -ktx 了！)
    // 它已经内置了 Kotlin 扩展功能
    implementation("com.google.firebase:firebase-database")

    // =======================================================
    // 3. 网络请求 (API 调用)
    // =======================================================
    // Retrofit (用来请求 Google Directions API)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    // Gson (用来把 API 返回的 JSON 文字变成 Kotlin 对象)
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // =======================================================
    // 4. 异步处理 (协程)
    // =======================================================
    // Kotlin 协程核心
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // 协程与 Google Play Services 的桥梁 (让你可以对 Firebase 任务使用.await())
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // =======================================================
    // 5. Android 基础与 UI 组件
    // =======================================================
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0") // 包含 BottomNavigationView
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.recyclerview) // 包含布局用的 ConstraintLayout

    // 单元测试 (系统默认保留)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
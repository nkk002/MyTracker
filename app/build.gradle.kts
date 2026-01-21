import org.gradle.kotlin.dsl.implementation

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("kotlin-parcelize")
}

android {
    namespace = "com.mmu.mytracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mmu.mytracker"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8

        isCoreLibraryDesugaringEnabled = true
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
    }
}

dependencies {
    // map
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.libraries.places:places:3.3.0")
    // gps
    implementation("com.google.android.gms:play-services-location:21.0.1")
    // polyline
    implementation("com.google.maps.android:android-maps-utils:3.8.0")

    //firebase
    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))
    implementation("com.google.firebase:firebase-firestore")

    // analytics
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-database")

    // Retrofit Google Directions API
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    // Gson
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.recyclerview)

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}
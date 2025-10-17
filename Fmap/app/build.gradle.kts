import java.util.Properties      // ✨ 1. 匯入 Properties 類別
import java.io.FileInputStream  // ✨ 2. 匯入 FileInputStream 類別

plugins {
    alias(libs.plugins.android.application)
    // alias(libs.plugins.kotlin.android) // 你的專案是 Java，所以這行可以移除或註解掉
    id("com.google.gms.google-services")
}

// ✨ 3. 在 android { ... } 區塊外面，加入這段讀取屬性檔案的邏輯
val localProperties = Properties() // ✨【修正】直接使用 Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile)) // ✨【修正】直接使用 FileInputStream()
}

android {
    namespace = "com.example.fmap"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.fmap"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // ✨ 在 release 版本中也建立這個 BuildConfig 欄位
            buildConfigField("String", "OPENAI_API_KEY", "\"${localProperties.getProperty("OPENAI_API_KEY")}\"")
        }
        debug {
            // ✨ 在 debug 版本中建立一個名為 OPENAI_API_KEY 的 BuildConfig 欄位
            buildConfigField("String", "OPENAI_API_KEY", "\"${localProperties.getProperty("OPENAI_API_KEY")}\"")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // 如果你的專案是純 Java，這個區塊可以移除
    // kotlinOptions {
    //     jvmTarget = "17"
    // }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // --- AndroidX / 基本元件 ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // --- Google Maps & Google Sign-In (Auth) ---
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // --- JSON (FavoritesStore 需要) ---
    implementation("com.google.code.gson:gson:2.11.0")

    // --- 圖片載入---
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // --- Firebase ---
    implementation(platform("com.google.firebase:firebase-bom:33.3.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")

    // --- 測試 ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // --- 網路請求 ---
    // Retrofit & OkHttp - 用於發送網路請求
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0") // OpenAIClient 需要
}

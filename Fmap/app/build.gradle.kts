plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // 用 catalog 的話通常是這個別名；若你沒在 toml 定義，就改成下一行的 id(...)
    // alias(libs.plugins.google.services)
    id("com.google.gms.google-services") // ← 確保至少有這行其中之一
}

android {
    buildFeatures { buildConfig = true }
    namespace = "com.example.fmap"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.fmap"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "USE_MOCK", "true")
            buildConfigField("String", "BASE_URL", "\"https://your.api/\"")
        }
        release {
            buildConfigField("boolean", "USE_MOCK", "false")
            buildConfigField("String", "BASE_URL", "\"https://your.api/\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // 建議 AGP 8.x 使用 JDK 17
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // GButton 來自 JitPack（settings.gradle.kts 已加入 jitpack repo）
    implementation("com.github.TutorialsAndroid:GButton:v1.0.19")

    // 🔥 Firebase：用 BoM 管版本
    implementation(platform("com.google.firebase:firebase-bom:33.2.0"))
    implementation("com.google.firebase:firebase-auth")

    // Google Sign-In（新版）
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // ⛔ 移除假的依賴，這一條會讓解析必炸
    // implementation("com.github.User:Repo:Tag")

    // 如果真的需要 Credential Manager 再保留，否則先拿掉以減少衝突
    // implementation(libs.androidx.credentials)
    // implementation(libs.androidx.credentials.play.services.auth)
    // implementation(libs.googleid)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

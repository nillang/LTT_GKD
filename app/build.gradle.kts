plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.ltt.gkd"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ltt.gkd"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests {
            // android.util.Log 等 Android stub 方法默认返回 0/null 而非抛 RuntimeException
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // AndroidX 基础
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Material Components（XML Theme.Material3.DayNight 宿主主题）
    implementation("com.google.android.material:material:1.12.0")

    // 数据存储
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // JSON 解析（规则文件，使用 KSP codegen）
    implementation("com.squareup.moshi:moshi:1.15.1")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")

    // 网络订阅
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 订阅自动更新（周期任务，系统调度，替代常驻前台服务）
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // MLKit 端侧 OCR（不依赖 Google Play 服务）
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")

    // 调试
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    // 单元测试
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.12")
    // Coroutines 测试支持
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    // Robolectric：让需要 Android Context/Keystore 的单元测试在 JVM 跑
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")  // ApplicationProvider 依赖

    // AndroidX Instrumented Test（androidTest）依赖
    androidTestImplementation("androidx.test.ext:junit:1.2.1")  // AndroidJUnit4 runner
    androidTestImplementation("androidx.test:runner:1.6.2")    // AndroidJUnitRunner
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")  // 协程测试支持（Dispatchers.setMain 等）
}

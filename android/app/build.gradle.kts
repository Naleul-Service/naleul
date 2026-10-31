import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun localProp(key: String): String = localProperties.getProperty(key, "")

android {
    namespace = "com.naleul.naleul"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.naleul.naleul"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"${localProp("naleul.kakaoNativeAppKey")}\"")
        manifestPlaceholders["KAKAO_APP_KEY"] = localProp("naleul.kakaoNativeAppKey")
        // Amplitude — 웹 NEXT_PUBLIC_AMPLITUDE_API_KEY 와 같은 키 (local.properties 의 naleul.amplitudeApiKey, 비어 있으면 안 보내요)
        buildConfigField("String", "AMPLITUDE_API_KEY", "\"${localProp("naleul.amplitudeApiKey")}\"")
        // AdMob — local.properties 의 naleul.admobAppId · naleul.admobBannerId (비어 있으면 앱 ID 는 구글 테스트 ID,
        // 배너는 Debug 에서만 테스트 광고 · Release 에서는 안 보여요)
        manifestPlaceholders["ADMOB_APP_ID"] = localProp("naleul.admobAppId").ifEmpty { "ca-app-pub-3940256099942544~3347511713" }
        buildConfigField("String", "ADMOB_BANNER_ID", "\"${localProp("naleul.admobBannerId")}\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"${localProp("naleul.baseUrl.debug")}\"")
            // 개발용 빌드에서만 http:// (내 컴퓨터의 로컬 서버) 접속 허용. 출시 빌드는 https 만 돼요
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            optimization {
                enable = false
            }
            buildConfigField("String", "BASE_URL", "\"${localProp("naleul.baseUrl.release")}\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kakao.sdk.user)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.compose.material.icons.core)
    // SF Symbols 대신 쓰는 머티리얼 아이콘 전체 (designsystem/SfIcon.kt 에서 이름 → 아이콘으로 바꿔요)
    implementation(libs.androidx.compose.material.icons.extended)
    // 분석은 Amplitude (웹 · iOS 와 같은 프로젝트). Firebase 는 푸시(FCM)에만 써요
    implementation(libs.amplitude.analytics)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // Google Play 구독 결제 (Play 정책상 Billing Library 8 이상)
    implementation(libs.play.billing.ktx)
    // 구글 캘린더 가져오기 — 캘린더 읽기 권한(access token)만 받아요
    implementation(libs.play.services.auth)
    // 광고 (AdMob) — Free · 둘러보기 사용자 홈 배너
    implementation(libs.play.services.ads)
}
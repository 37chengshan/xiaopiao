plugins {
    alias(libs.plugins.android.application)
}

/**
 * Test-only app that posts Alipay / WeChat shaped notifications so the capture pipeline can be
 * exercised without the real apps. Never shipped: :app does not depend on it, and the package name
 * is a spoof of the real apps, so it MUST only ever be installed on an emulator.
 */
android {
    namespace = "com.xiaopiao.notifake"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    flavorDimensions += "spoof"
    productFlavors {
        create("wechat") {
            dimension = "spoof"
            applicationId = "com.tencent.mm"
            resValue("string", "fake_app_name", "Fake WeChat")
        }
        create("alipay") {
            dimension = "spoof"
            applicationId = "com.eg.android.AlipayGphone"
            resValue("string", "fake_app_name", "Fake Alipay")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        resValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

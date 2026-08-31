plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.golda.patchertiktok"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.golda.patchertiktok"
        minSdk = 24
        targetSdk = 36
        versionCode = 36
        versionName = "3.11"

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    testImplementation(libs.junit)
    compileOnly(files("libs/xposed-api-82.jar"))
}

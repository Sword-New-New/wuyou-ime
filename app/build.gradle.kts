import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 正式签名配置：keystore.properties 不入库（见 .gitignore）。
// 没有这个文件时，release 会自动退回 debug 签名，保证 clone 下来就能构建。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val hasReleaseKey = keystorePropsFile.exists() &&
    keystoreProps.getProperty("storeFile")?.isNotBlank() == true

android {
    namespace = "com.wuyouai.ime"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wuyouai.ime"
        minSdk = 26          // Android 8.0
        targetSdk = 34
        versionCode = 20
        versionName = "2.0.0"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                // 相对仓库根目录的路径
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ""
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module")
    }
}

dependencies {
    // 全部为 AndroidX 本地库，无任何联网/统计/广告 SDK
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // DataStore（本地键值存储，替代 SharedPreferences）
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // 注意：刻意不引入任何含 INTERNET 行为的三方库
}

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// İmza bilgileri depoya girmez: proje kökündeki keystore.properties dosyasından okunur.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.eirahoutmoss.nexsense"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eirahoutmoss.nexsense"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"

        // Güncelleme denetiminin baktığı GitHub deposu (Nex standardı).
        buildConfigField("String", "UPDATE_REPO_OWNER", "\"Eirahoutmoss\"")
        buildConfigField("String", "UPDATE_REPO_NAME", "\"nexsense\"")
    }

    androidResources {
        // Android 13+ "Uygulama dili" ayarı için dil listesi otomatik üretilir (res/resources.properties).
        generateLocaleConfig = true
    }

    signingConfigs {
        // Deneme derlemeleri her bilgisayarda ve GitHub'da aynı anahtarla imzalansın (üstüne kurulabilsin).
        // Yalnızca .debug paketi içindir; gerçek sürüm anahtarı depoya girmez.
        getByName("debug") {
            val ciKey = rootProject.file("tools/ci/debug.keystore")
            if (ciKey.exists()) {
                storeFile = ciKey
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
        buildConfig = true
    }
    lint {
        // Hatalar derlemeyi durdurur; uyarılar raporda kalır (app/build/reports/lint-results-debug.html).
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = false
        textReport = true
        textOutput = file("build/reports/lint-results-debug.txt")
        // Bağımlılık sürümleri bilerek NexTools/NexYapı ile aynı tutulur (bkz. gradle/libs.versions.toml).
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable", "OldTargetApi")
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    // Otomatik ölçüm: hareket izleme, nokta bulutu, düzlemler (ARCore desteklemeyen cihazda adım tabanlı mod)
    implementation(libs.arcore)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}

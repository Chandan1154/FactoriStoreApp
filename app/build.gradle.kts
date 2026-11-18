plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

val storeAppName = providers.gradleProperty("STORE_APP_NAME").orElse("Zolara")
val storeAppId = providers.gradleProperty("STORE_APP_ID")
    .orElse("com.factori.store.in.zolara.a6d5ab36280")
val storeUrl = providers.gradleProperty("STORE_URL").orElse("https://zolara.in")
val launcherIcon = providers.gradleProperty("STORE_LAUNCHER_ICON").orElse("@mipmap/ic_launcher")
val storeIconRes = providers.gradleProperty("STORE_ICON_RES")
val storeVersionCode = providers.gradleProperty("STORE_VERSION_CODE").orElse("1")
val storeVersionName = providers.gradleProperty("STORE_VERSION_NAME").orElse("1.0")
val signingStoreFile = System.getenv("STORE_SIGNING_STORE_FILE")
val signingStorePassword = System.getenv("STORE_SIGNING_STORE_PASSWORD")
val signingKeyAlias = System.getenv("STORE_SIGNING_KEY_ALIAS")
val signingKeyPassword = System.getenv("STORE_SIGNING_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    signingStoreFile,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.factori.storeapp"
    compileSdk = 34

    defaultConfig {
        applicationId = storeAppId.get()
        minSdk = 24
        targetSdk = 34
        versionCode = storeVersionCode.get().toInt()
        versionName = storeVersionName.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("string", "app_name", storeAppName.get())
        manifestPlaceholders["launcherIcon"] = launcherIcon.get()
        resValue("string", "storefront_url", storeUrl.get())
    }

    if (storeIconRes.isPresent) {
        sourceSets.getByName("main").res.srcDir(file(storeIconRes.get()))
    }

    if (hasReleaseSigning) {
        signingConfigs.create("storeRelease") {
            storeFile = file(signingStoreFile!!)
            storePassword = signingStorePassword
            keyAlias = signingKeyAlias
            keyPassword = signingKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("storeRelease")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
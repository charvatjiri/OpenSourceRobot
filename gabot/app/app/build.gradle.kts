plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val majorVer = 0
val minorVer = 3
val microVer = 1

android {
    namespace = "com.gabotapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gabotapp"
        minSdk = 24
        targetSdk = 36
        versionCode = majorVer * 10000 + minorVer * 100 + microVer
        versionName = "$majorVer.$minorVer.$microVer"

        buildConfigField("int", "MAJOR_VER", "$majorVer")
        buildConfigField("int", "MINOR_VER", "$minorVer")
        buildConfigField("int", "MICRO_VER", "$microVer")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "11"
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "GabotApp-$majorVer.$minorVer.$microVer-${variant.buildType.name}.apk"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.usb.serial)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

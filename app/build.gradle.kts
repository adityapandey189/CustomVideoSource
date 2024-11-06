plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.customvideosource"
    compileSdk = 34

    buildFeatures {
        dataBinding = true
        viewBinding = true

    }




    defaultConfig {
        applicationId = "com.example.customvideosource"
        minSdk = 24
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"
        renderscriptTargetApi = 19
        renderscriptSupportModeEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {

    implementation ("androidx.databinding:databinding-runtime:8.7.1")
    implementation("com.dafruits:webrtc:123.0.0")

    implementation ("pub.devrel:easypermissions:3.0.0")
    implementation("io.socket:socket.io-client:1.0.0") {
        exclude(group = "org.json", module = "json")
    }

    androidTestImplementation ("androidx.test.espresso:espresso-core:3.6.1")

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
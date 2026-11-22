plugins {
    id("com.android.application")
    id("kotlin-android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.elvan.gallery"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.elvan.gallery"
        minSdk = 27
        targetSdk = 34
        versionCode = 1
        versionName = "1.0 Elvan Navil"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Release speed, signed with the debug key so it installs over the debug app: for measuring and phone testing
        create("profile") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    // Elvan Navil UI (the shell, header fold and snap, pills, navbar, colours, font, icons)
    implementation("com.elvan:elvanNavil:0.2.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")

    // Thumbnails and photos
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-video:3.0.4")

    implementation("androidx.print:print:1.0.0")
    // Videos play inside the viewer
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    // The video editor: trim, rotate, filters/tone (colour LUT), text, speed, mute, exported by Transformer
    implementation("androidx.media3:media3-transformer:1.8.0")
    implementation("androidx.media3:media3-effect:1.8.0")

    testImplementation("junit:junit:4.13.2")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.musclejournal.app"
    compileSdk {
        version = release(37)
    }
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.musclejournal.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "0.4.0"
        testInstrumentationRunner = "com.musclejournal.app.JournalAcceptanceInstrumentation"
    }

    buildTypes {
        getByName("debug") { buildConfigField("boolean", "TEST_TOOLS", "true") }
        getByName("release") {
            buildConfigField("boolean", "TEST_TOOLS", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        named("debug") { assets.directories += rootProject.file("tmp/fixtures/android").absolutePath }
        named("main") {
            manifest.srcFile(rootProject.file("src/main/AndroidManifest.xml"))
            kotlin.directories.clear()
            kotlin.directories += rootProject.file("src/main/kotlin").absolutePath
            res.directories.clear()
            res.directories += rootProject.file("src/main/res").absolutePath
            assets.directories.clear()
            assets.directories += rootProject.file("src/main/assets").absolutePath
        }
        named("test") {
            kotlin.directories.clear()
            kotlin.directories += rootProject.file("src/test/kotlin").absolutePath
            java.directories.clear()
        }
        named("androidTest") {
            kotlin.directories.clear()
            kotlin.directories += rootProject.file("src/androidTest/kotlin").absolutePath
            java.directories.clear()
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
}

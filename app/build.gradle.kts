import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
}

// Optional release signing. Absent on contributor machines and CI, so the
// release build silently falls back to unsigned — debug builds never need it.
// Populate app/keystore.properties (gitignored) to sign locally.
val keystorePropertiesFile = rootProject.file("app/keystore.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            load(FileInputStream(keystorePropertiesFile))
        }
    }

// AdMob ids. A debug build always serves Google's SAMPLE ids (labelled test
// ads that earn nothing — clicking one's own live ads gets an AdMob account
// suspended), so only release reads app/admob.properties (gitignored; see
// admob.properties.example and just-my-weather-1zp). Absent, release falls
// back to the samples with a warning so a contributor's bundleRelease still
// assembles; scripts/android/release-internal.sh refuses to upload samples.
val admobPropertiesFile = rootProject.file("app/admob.properties")
val admobProperties =
    Properties().apply {
        if (admobPropertiesFile.exists()) {
            load(FileInputStream(admobPropertiesFile))
        }
    }
val admobSampleAppId = "ca-app-pub-3940256099942544~3347511713"
val admobSampleBannerId = "ca-app-pub-3940256099942544/9214589741"
val admobAppId = admobProperties.getProperty("appId") ?: admobSampleAppId
val admobBannerId = admobProperties.getProperty("bannerUnitId") ?: admobSampleBannerId
if (!admobPropertiesFile.exists()) {
    logger.warn(
        "app/admob.properties missing: release will serve AdMob SAMPLE ads " +
            "(fine for internal testing, never for production)",
    )
}

android {
    namespace = "io.raylytics.justmyweather"
    // Google Play has required new apps and updates to target API 36 since
    // 2026-08-31; the internal track is not exempt. AGP 8.13 is the matching
    // toolchain (8.7 only warned at 36).
    compileSdk = 36

    defaultConfig {
        applicationId = "io.raylytics.justmyweather"
        minSdk = 24
        targetSdk = 36
        // versionCode is bumped per upload by scripts/android/bump-version-code.sh;
        // versionName is the release (CLAUDE.md "Versioning"). 0.2.0 is the first
        // Play build: everything since the v0.1.x sideloads.
        versionCode = 4
        versionName = "0.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Ship only the English resources we wrote. Without this, the AAB
        // carries every locale AndroidX / Compose / Material 3 translate
        // into. Add target locales here when in-app copy is localised.
        resourceConfigurations += listOf("en")

        // The Google Mobile Ads SDK reads its app id from the manifest; the
        // banner unit reaches AdBanner through BuildConfig. Debug pins both to
        // the samples (AdPolicy.bannerUnitId re-checks the unit at runtime).
        manifestPlaceholders["admobAppId"] = admobSampleAppId
        buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"$admobSampleBannerId\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties.getProperty("storeFile") ?: "release.keystore")
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            manifestPlaceholders["admobAppId"] = admobAppId
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"$admobBannerId\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// Kotlin 2.3 (bumped 2026-10-03 for the Google Mobile Ads and Play Billing
// SDKs, whose metadata 2.1 could not read) retired the kotlinOptions DSL.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Weather data layer: NWS over HTTP, JSON wire shapes parsed with
    // kotlinx.serialization. No proprietary SDKs — keeps the app
    // open-source-friendly (e.g. F-Droid) and the data path legible.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Persistence for the user's view config, theme, location, and alert
    // rules. DataStore over SharedPreferences for the flow-based reads.
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Background polling for personal alerts.
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // The one banner ad and the purchase that removes it (Evan, 2026-10-03).
    // Both are Google services the Play build cannot avoid; they stay behind
    // ads/ and billing/ so a build without them (F-Droid, say) would drop two
    // packages and one call site. Play requires Billing Library 8+ for new
    // apps since 2026-08-31.
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.android.billingclient:billing-ktx:9.1.0")
    // The ads SDK pulls in a pre-1.3 androidx.fragment, and release lint
    // (lintVitalRelease) refuses an app that registers for activity results
    // on one (InvalidFragmentVersionForActivityResult). Pinned so the
    // resolved version is one that calls super.onRequestPermissionsResult.
    implementation("androidx.fragment:fragment:1.8.5")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.3")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.3")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // Pinned ahead of what compose-bom drags in. Espresso 3.5 resolves
    // android.hardware.input.InputManager.getInstance by reflection, and that
    // method is gone in Android 17 — every instrumented test dies at
    // Espresso.onIdle before its body runs. See just-my-weather-1te.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
    }
}

import com.android.build.OutputFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
}

val diagnosticBuildSha = providers.environmentVariable("GITHUB_SHA").orNull
    ?.take(12)
    ?: runCatching {
        providers.exec {
            commandLine("git", "rev-parse", "--short=12", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim()
    }.getOrDefault("unknown").ifBlank { "unknown" }

val appVersionCode = 100
val appVersionName = "2.0.0"

android {
    namespace = "com.zenx.yugen.play"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.zenx.yugen.play"
        // Fire OS 5 devices (including 1st/2nd-gen Fire TV Sticks) report API 22.
        minSdk = 22
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "GIT_SHA", "\"$diagnosticBuildSha\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing credentials and private API keys are injected exclusively
    // inside the private build environment and omitted from this public showcase.
    buildTypes {
        debug {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // One codebase, two shipped apps (Mobile & Android TV) sharing identical applicationId
    // so updates and user libraries remain continuous while keeping form-factor dependencies isolated.
    flavorDimensions += "form"
    productFlavors {
        create("mobile") {
            dimension = "form"
            isDefault = true
        }
        create("tv") {
            dimension = "form"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
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
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // Publish targeted ARM splits alongside a universal APK for sideloaded installs.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val abi = output.getFilter(OutputFile.ABI)
            val buildTypeSuffix = if (buildType.name == "release") "" else "-${buildType.name}"
            val formSuffix = if (flavorName == "tv") "_tv" else ""
            output.outputFileName = if (abi == null) {
                "YugenPlay$buildTypeSuffix$formSuffix.apk"
            } else {
                "YugenPlay$buildTypeSuffix${formSuffix}_$abi.apk"
            }
        }
    }
}

listOf("mobile", "tv").forEach { flavor ->
    val variant = "${flavor}Release"
    val archiveTask = tasks.register<Copy>("archive${variant.replaceFirstChar(Char::uppercase)}Mapping") {
        dependsOn("minify${variant.replaceFirstChar(Char::uppercase)}WithR8")
        inputs.property("buildSha", diagnosticBuildSha)
        inputs.property("versionName", appVersionName)
        inputs.property("versionCode", appVersionCode)
        from(layout.buildDirectory.file("outputs/mapping/$variant/mapping.txt"))
        into(layout.buildDirectory.dir("outputs/mapping-archive"))
        rename(
            "mapping.txt",
            "mapping-$flavor-v$appVersionName-$appVersionCode-$diagnosticBuildSha.txt",
        )
    }
    tasks.matching { it.name == "assemble${variant.replaceFirstChar(Char::uppercase)}" }
        .configureEach { finalizedBy(archiveTask) }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.material3)
    implementation(libs.material.color.utilities)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.datasource.cronet)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.database)
    // Cast is mobile-only so TV builds never pull Play Services Cast onto Leanback/Fire TV devices.
    "mobileImplementation"(libs.androidx.media3.cast)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.play.services.cronet)
    implementation(libs.androidx.appcompat)
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.fragment.ktx)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.okio)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.qrcodegen)
    implementation(libs.haze)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.tvprovider)
    implementation(libs.androidx.profileinstaller)
    // Schnorr / ECDH primitives for TV pairing and update manifest verification.
    implementation(libs.acinq.secp256k1.android)
    findProject(":benchmark")?.let { baselineProfile(it) }
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.acinq.secp256k1.jvm)
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

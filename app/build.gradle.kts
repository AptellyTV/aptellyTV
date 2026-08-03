import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
}

val releaseKeystoreFile = System.getenv("APTELLY_KEYSTORE_FILE")
val releaseKeystorePassword = System.getenv("APTELLY_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("APTELLY_KEY_ALIAS")
val releaseKeyPassword = System.getenv("APTELLY_KEY_PASSWORD")
val releaseSigningReady = listOf(
    releaseKeystoreFile,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }
val posterFeedUrl = System.getenv("APTELLY_POSTER_FEED_URL")
    ?: "https://aptelly-poster-feed.aptelly.workers.dev/poster-feed.json?v=7"
val matchApiBaseUrl = System.getenv("APTELLY_MATCH_API_BASE_URL")
    ?: "https://aptelly-install-matcher.aptelly.workers.dev"
val enableTestFixtures = System.getenv("APTELLY_ENABLE_TEST_FIXTURES")
    ?.equals("true", ignoreCase = true) == true
val allowCleartextTest = System.getenv("APTELLY_ALLOW_CLEARTEXT_TEST")
    ?.equals("true", ignoreCase = true) == true
val escapedPosterFeedUrl = posterFeedUrl
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
val escapedMatchApiBaseUrl = matchApiBaseUrl
    .trimEnd('/')
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

val bundledClashVersion = "2.11.32"
val bundledClashFileName = "cmfa-${bundledClashVersion}-meta-universal-release.apk"
val bundledClashUrl =
    "https://github.com/MetaCubeX/ClashMetaForAndroid/releases/download/" +
        "v${bundledClashVersion}/${bundledClashFileName}"
val bundledClashSha256 =
    "f0eb8d15c6f5c8845dae8bac5bd4ead273b168672c750299d42252c8fb1f28cb"
val generatedBootstrapAssets = layout.buildDirectory.dir("generated/bootstrapAssets")

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val prepareBundledClash by tasks.registering {
    val outputFile = generatedBootstrapAssets.map {
        it.file("bootstrap/clash-meta-universal.apk")
    }
    outputs.file(outputFile)

    doLast {
        val cacheDirectory = File(
            gradle.gradleUserHomeDir,
            "caches/aptelly-bootstrap/clash-meta"
        )
        val cachedApk = File(cacheDirectory, bundledClashFileName)
        val partialApk = File(cacheDirectory, "${bundledClashFileName}.part")
        cacheDirectory.mkdirs()

        if (!cachedApk.isFile || sha256(cachedApk) != bundledClashSha256) {
            cachedApk.delete()
            partialApk.delete()
            URI(bundledClashUrl).toURL().openStream().buffered().use { input ->
                partialApk.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }
            val actual = sha256(partialApk)
            check(actual == bundledClashSha256) {
                "Bundled Clash APK hash mismatch: $actual"
            }
            check(partialApk.renameTo(cachedApk)) {
                "Unable to finalize bundled Clash APK cache"
            }
        }

        val generatedApk = outputFile.get().asFile
        generatedApk.parentFile.mkdirs()
        cachedApk.copyTo(generatedApk, overwrite = true)
    }
}

android {
    namespace = "app.aptelly.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.aptelly.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = 48
        versionName = "0.12.25"
        manifestPlaceholders["usesCleartextTraffic"] = allowCleartextTest.toString()

        buildConfigField("boolean", "FORCE_HYPER_OS", "false")
        buildConfigField(
            "String",
            "POSTER_FEED_URL",
            "\"${escapedPosterFeedUrl}\""
        )
        buildConfigField(
            "String",
            "MATCH_API_BASE_URL",
            "\"${escapedMatchApiBaseUrl}\""
        )
        buildConfigField(
            "boolean",
            "ENABLE_TEST_FIXTURES",
            enableTestFixtures.toString()
        )
        buildConfigField(
            "boolean",
            "ALLOW_CLEARTEXT_TEST",
            allowCleartextTest.toString()
        )
        buildConfigField(
            "String",
            "BUNDLED_CLASH_VERSION",
            "\"${bundledClashVersion}.Meta\""
        )
        buildConfigField(
            "String",
            "BUNDLED_CLASH_SHA256",
            "\"${bundledClashSha256}\""
        )
    }

    signingConfigs {
        create("directRelease") {
            if (releaseSigningReady) {
                storeFile = file(releaseKeystoreFile!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "FORCE_HYPER_OS", "true")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("directRelease")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets.getByName("main").assets.srcDir(generatedBootstrapAssets)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareBundledClash)
}

dependencies {
    implementation("androidx.core:core:1.16.0")
    testImplementation("junit:junit:4.13.2")
}

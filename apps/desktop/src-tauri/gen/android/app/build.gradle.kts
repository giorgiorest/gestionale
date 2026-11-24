import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("rust")
}

val tauriProperties = Properties().apply {
    val propFile = file("tauri.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { load(it) }
    }
}

// Firma dell'APK di release (vedi apps/desktop/README.md, "APK Android").
// `key.properties` non sta nel repository: in locale lo scrive chi compila, in CI lo genera
// il workflow android.yml dai GitHub Secrets. Senza il file la build di release si ferma
// con un errore, invece di produrre un APK non firmato che nessun telefono installa.
// Il file sta in gen/android/ (accanto a settings.gradle), e `storeFile` si scrive relativo
// a quella cartella oppure assoluto.
val keyPropertiesFile = rootProject.file("key.properties")
val keyProperties = Properties().apply {
    if (keyPropertiesFile.exists()) {
        keyPropertiesFile.inputStream().use { load(it) }
    }
}
val signingConfigured = keyProperties.getProperty("storeFile") != null

android {
    compileSdk = 37
    namespace = "it.gestionale.desktop"
    signingConfigs {
        if (signingConfigured) {
            create("release") {
                storeFile = rootProject.file(keyProperties.getProperty("storeFile"))
                storePassword = keyProperties.getProperty("storePassword")
                keyAlias = keyProperties.getProperty("keyAlias")
                keyPassword = keyProperties.getProperty("keyPassword")
            }
        }
    }
    defaultConfig {
        manifestPlaceholders["usesCleartextTraffic"] = "false"
        applicationId = "it.gestionale.desktop"
        minSdk = 24
        targetSdk = 37
        versionCode = tauriProperties.getProperty("tauri.android.versionCode", "1").toInt()
        versionName = tauriProperties.getProperty("tauri.android.versionName", "1.0")
    }
    buildTypes {
        getByName("debug") {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            isDebuggable = true
            isJniDebuggable = true
            isMinifyEnabled = false
            packaging {
                jniLibs.keepDebugSymbols.add("*/arm64-v8a/*.so")
                jniLibs.keepDebugSymbols.add("*/armeabi-v7a/*.so")
                jniLibs.keepDebugSymbols.add("*/x86/*.so")
                jniLibs.keepDebugSymbols.add("*/x86_64/*.so")
            }
        }
        getByName("release") {
            if (signingConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
               enable = true
            }
            proguardFiles(
                *fileTree(".") {
                  include("**/*.pro")
                  exclude("build/**")
                }.files.toTypedArray()
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

rust {
    rootDirRel = "../../../"
}

dependencies {
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.0")
}

// Senza chiave di firma la build di release si ferma qui, con un messaggio che dice cosa manca.
// Il controllo è sul singolo task (non in configurazione) perché `tauri android dev` e le build
// di debug, firmate con la chiave di debug di Android, devono continuare a funzionare.
if (!signingConfigured) {
    val missingKey = "Firma Android non configurata: manca ${keyPropertiesFile.path}.\n" +
        "In locale: creare il file con storeFile, storePassword, keyAlias, keyPassword " +
        "(istruzioni in apps/desktop/README.md).\n" +
        "In CI: il workflow .github/workflows/android.yml lo genera dai secret " +
        "ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD."
    tasks.matching {
        it.name.contains("Release") &&
            (it.name.startsWith("assemble") || it.name.startsWith("bundle") || it.name.startsWith("package"))
    }.configureEach {
        doFirst { throw GradleException(missingKey) }
    }
}

apply(from = file("tauri.build.gradle.kts"))

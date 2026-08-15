plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.game3d_opengl"
    compileSdk = 34

    defaultConfig {
        // A separate ID lets performance builds coexist with an installed APK that was
        // signed by a different debug key: -PgameApplicationId=com.example.game3d_opengl.perf
        applicationId = providers.gradleProperty("gameApplicationId")
            .getOrElse("com.example.game3d_opengl")
        minSdk = 28
        targetSdk = 34
        versionCode = 2
        versionName = "1.01"

        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        // Fast smoke-run defaults for on-device benchmark iteration time.
        testInstrumentationRunnerArguments["androidx.benchmark.iterations"] = "1"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "LOW_BATTERY"
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

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
            assets.srcDirs(
                "src/main/assets",
                "../wheel-mesh-lab/exports",
                "../terrain-content/published",
            )
        }
        getByName("test").java.srcDirs("src/test/java")
        getByName("androidTest").java.srcDirs("src/androidTest/java")
    }

    testBuildType = "release"
}

dependencies {

    implementation(project(":game-core"))
    implementation(project(":terrain-authoring"))
    implementation(project(":terrain-io"))
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.benchmark.common)
    implementation(libs.androidx.junit)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.benchmark.junit4)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    testLogging {
        events(
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
        )
    }

    addTestListener(object : org.gradle.api.tasks.testing.TestListener {
        override fun beforeSuite(suite: org.gradle.api.tasks.testing.TestDescriptor) = Unit

        override fun beforeTest(testDescriptor: org.gradle.api.tasks.testing.TestDescriptor) = Unit

        override fun afterTest(
            testDescriptor: org.gradle.api.tasks.testing.TestDescriptor,
            result: org.gradle.api.tasks.testing.TestResult
        ) = Unit

        override fun afterSuite(
            suite: org.gradle.api.tasks.testing.TestDescriptor,
            result: org.gradle.api.tasks.testing.TestResult
        ) {
            if (suite.parent == null) {
                logger.lifecycle(
                    "${path} summary: ${result.testCount} tests, " +
                        "${result.successfulTestCount} passed, " +
                        "${result.failedTestCount} failed, " +
                        "${result.skippedTestCount} skipped"
                )
            }
        }
    })
}

tasks.named("preBuild") {
    dependsOn(rootProject.tasks.named("validateTerrainContent"))
}

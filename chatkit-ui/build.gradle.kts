
/*
 * Copyright (c) 2022 NetEase, Inc.  All rights reserved.
 * Use of this source code is governed by a MIT license that can be found in the LICENSE file.
 */

plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    compileSdk = 30

    defaultConfig {
        minSdk = 31
        targetSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    api("com.netease.yunxin.kit.chat:chatkit:1.0.0-beta03")
    api("com.netease.yunxin.kit.common:common-ui:1.0.4")
    implementation("androidx.appcompat:appcompat:1.4.1") 
    implementation("com.google.android.material:material:1.5.0") 
    implementation("androidx.recyclerview:recyclerview:1.2.1") 
    implementation("com.airbnb.android:lottie:5.0.3") 
    implementation("com.github.bumptech.glide:glide:4.13.1") 
    implementation("com.netease.yunxin.kit.common:common:1.0.4")

    testImplementation("junit:junit:4.13.2") 
    androidTestImplementation("androidx.test.ext:junit:1.1.3") 
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}

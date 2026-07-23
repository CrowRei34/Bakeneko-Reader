plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose") version "1.6.11"
    id("app.cash.sqldelight") version "2.0.2"
}

sqldelight {
    databases {
        create("FutonDatabase") {
            packageName.set("io.github.landwarderer.futon.desktop.db")
        }
    }
}

kotlin {
    jvm("desktop")
    
    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation("com.github.AppFuton:futon-parsers:f287c414a6")
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                implementation("org.openjdk.nashorn:nashorn-core:15.4")
                implementation("app.cash.sqldelight:sqlite-driver:2.0.2")
                implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "FutonDesktop"
            packageVersion = "1.0.0"
        }
    }
}

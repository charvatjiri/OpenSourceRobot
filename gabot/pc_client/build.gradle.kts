import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

group = "com.gabot"
version = "0.1.3"

kotlin {
    jvmToolchain(21)
    sourceSets.main {
        kotlin.srcDir("../shared/src/main/kotlin")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("com.fazecast:jSerialComm:2.11.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.gabot.pcclient.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi)
            packageName = "GabotPcClient"
            packageVersion = project.version.toString()
            description = "Touch and mouse controller for GABOT over Bluetooth serial"
            vendor = "GABOT"
            linux {
                shortcut = true
                menuGroup = "Utility"
            }
            windows {
                shortcut = true
                menuGroup = "GABOT"
                upgradeUuid = "c5574b59-843a-4b19-bd41-eb1fc026f559"
            }
        }
    }
}

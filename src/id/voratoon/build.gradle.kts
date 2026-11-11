import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

android {
    sourceSets.named("test") {
        java.directories.clear()
        java.directories.add("tests")
        kotlin.directories.clear()
        kotlin.directories.add("tests")
    }
}

// Unit tests have no source declarations for the extension metadata processor.
tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("UnitTestKotlin") }.configureEach {
    enabled = false
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.stdlib)
    testImplementation(libs.okhttp.core)
    testImplementation(libs.kotlin.json)
    testImplementation(libs.tachiyomi.lib.v16)
}

keiyoushi {
    name = "VoraToon"
    pkgName = "id.komikcast"
    versionCode = 91
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    deeplink {
        path("/series/..*")
    }

    source {
        lang = "id"
        baseUrl {
            custom("https://13.voratoon.com")
        }
        id = 972717448578983812L
    }
}

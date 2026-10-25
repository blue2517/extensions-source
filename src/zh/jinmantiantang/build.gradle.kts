import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Jinman Tiantang"
    versionCode = 61
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "禁漫天堂"
        lang = "zh"
        baseUrl = "https://18comic.vip"
    }

    deeplink {
        host("18comic.vip")
        host("18comic.ink")
        host("jmcomic-zzz.one")
        host("jmcomic-zzz.org")
        path("/album/..*")
    }
}

android {
    sourceSets {
        named("test") {
            java.directories.add("test")
            kotlin.directories.add("test")
        }
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.core)
}

// Helper tests do not declare another @Source entry point for the source processor.
tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("UnitTestKotlin") }.configureEach {
    enabled = false
}

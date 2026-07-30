import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    // Плагин чисто на Kotlin: нет Java-исходников и GUI-форм (.form),
    // инструментирование байткода не нужно.
    instrumentCode = false

    // Одно простое поле настроек не стоит индексации через запуск headless-IDE
    // на каждой сборке (к тому же конфликтует с открытым runIde по блокировке
    // песочницы).
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            // Только PhpStorm 2026.2.x
            sinceBuild = "262"
            untilBuild = "262.*"
        }
    }

    pluginVerification {
        ides {
            // Целевая IDE — только PhpStorm 2026.2.
            create(IntelliJPlatformType.PhpStorm, "2026.2.0.1")
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        phpstorm("2026.2.0.1")
        bundledPlugin("com.jetbrains.php")
        testFramework(TestFrameworkType.Platform)
    }
}

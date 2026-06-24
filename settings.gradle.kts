plugins {
    // Permite ao Gradle baixar automaticamente o JDK 21 (toolchain) se não houver um instalado.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "claude-commit-message"

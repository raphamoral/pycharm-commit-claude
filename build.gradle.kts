plugins {
    id("java")
    // IntelliJ Platform Gradle Plugin 2.x
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.raphaelmoral"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Roda/empacota contra o PyCharm Community. A barra de mensagem de commit
        // é uma feature de plataforma, então o plugin também funciona no IntelliJ IDEA.
        pycharmCommunity("2024.2")
        // Módulo que traz a engine de patch (IdeaTextPatchBuilder/UnifiedDiffWriter)
        // usada para gerar o unified diff.
        bundledModule("intellij.platform.vcs.impl")
    }
    // Gson para montar/parsear o JSON da API da Anthropic.
    implementation("com.google.code.gson:gson:2.10.1")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
            untilBuild = "253.*"
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

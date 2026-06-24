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

    // Assinatura do plugin (exigida pelo JetBrains Marketplace).
    // Gere o par de chaves conforme https://plugins.jetbrains.com/docs/intellij/plugin-signing.html
    // e exporte as variáveis de ambiente abaixo antes de rodar `./gradlew signPlugin`.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    // Publicação no Marketplace. Pegue o token em
    // https://plugins.jetbrains.com/author/me/tokens e exporte como PUBLISH_TOKEN.
    // Depois rode `./gradlew publishPlugin`.
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // Canal "default" = público estável. Use "beta"/"eap" para canais separados.
        channels = listOf("default")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

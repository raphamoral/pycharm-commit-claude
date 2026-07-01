import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    // IntelliJ Platform Gradle Plugin 2.x
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.raphaelmoral"
version = "0.1.6"

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
            // Piso: 2023.3. IDEs 2023.3–2024.1 rodam no JBR 17, por isso compilamos
            // o plugin em bytecode 17 (ver tasks.withType<JavaCompile> abaixo).
            sinceBuild = "233"
            // Sem teto: o plugin usa apenas APIs estáveis de plataforma/VCS, então fica
            // compatível com builds futuras (243, 251, 252, 253, 261, ...) sem precisar
            // republicar a cada release. provider { null } remove o atributo until-build
            // (string vazia é inválida: escreve until-build="" e o validador rejeita).
            untilBuild = provider { null }
        }

        changeNotes = """
            <ul>
              <li>Strip Markdown code fences (```) that the model occasionally wraps around the message, so the commit field gets the plain message.</li>
              <li>Replaced the deprecated ReadAction.compute(...) call with ReadAction.nonBlocking(...).executeSynchronously() to stay compatible with newer IDE builds.</li>
              <li>Commit message language picker now offers the full set of supported languages (around 90 entries, including regional variants such as en-GB, pt-PT, es-419, fr-CA and zh-Hans/zh-Hant). The field stays editable, so any other language code still works.</li>
              <li>Auto-detect git-bash on native Windows and set CLAUDE_CODE_GIT_BASH_PATH so the CLI runs without manual setup; added an optional git-bash path setting.</li>
              <li>On Windows "auto" mode, try the native and WSL runtimes in turn and fall back when one fails; the runtime that works is saved as the default. Only when both fail do we report that you're not logged in.</li>
            </ul>
        """.trimIndent()
    }

    // IDEs usados pelo verifyPlugin (verificador de compatibilidade da JetBrains).
    // Checamos as pontas da faixa suportada: o piso (sinceBuild) e uma build recente.
    pluginVerification {
        ides {
            ide(IntelliJPlatformType.PyCharmCommunity, "2023.3")
            ide(IntelliJPlatformType.PyCharmCommunity, "2025.3")
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

tasks.withType<JavaCompile>().configureEach {
    // Gera bytecode Java 17 para o plugin carregar nas IDEs anteriores à 2024.2
    // (que rodam no JBR 17), sem afetar as 2024.2+ (que aceitam 17). A compilação
    // continua usando o JDK 21 do toolchain.
    options.release = 17
}

# Claude Commit Message — Plugin para PyCharm / IntelliJ

Adiciona um botão na barra de ferramentas da **mensagem de commit** (o mesmo lugar onde
aparece o botão da *AI Assistant*). Ao clicar, o plugin pega o diff dos arquivos do commit,
manda para a **Claude** (API da Anthropic) e escreve a mensagem de commit gerada no campo.

## Como funciona

1. O botão é registrado no grupo de ações `Vcs.MessageActionGroup`, que é a barrinha de
   ícones ao lado do campo de mensagem de commit.
2. Ao clicar, ele coleta os `Change`s (alterações selecionadas, ou a changelist padrão se
   nada estiver selecionado) e gera um *unified diff* com a mesma engine do "Create Patch".
3. Faz um `POST` para `https://api.anthropic.com/v1/messages` com o diff.
4. O texto retornado vai direto para o campo de commit.

A API key é guardada no **PasswordSafe** da IDE (keychain do SO), não em arquivo de config.

## Pré-requisitos

- IntelliJ IDEA ou PyCharm **2024.2+** (para abrir/buildar o projeto — ele traz o JDK).
- Uma **API key da Anthropic** (https://console.anthropic.com/).

> Não é necessário ter Java/Gradle instalados no sistema: ao abrir o projeto no IntelliJ,
> ele usa o JBR embutido e provisiona o JDK 21 via toolchain automaticamente.

## Build e execução (desenvolvimento)

Abra a pasta no IntelliJ IDEA como projeto Gradle. Depois, na aba **Gradle**:

- **`runIde`** — sobe uma instância sandbox do PyCharm Community já com o plugin instalado.
  Abra qualquer projeto com Git, vá no commit e o botão (ícone de lâmpada) estará ao lado
  do campo de mensagem.
- **`buildPlugin`** — gera o `.zip` distribuível em `build/distributions/`.

Pela linha de comando (se tiver Java 21 + Gradle, ou gerar o wrapper com `gradle wrapper`):

```bash
./gradlew runIde       # roda em sandbox
./gradlew buildPlugin   # gera o zip
```

## Instalar no seu PyCharm do dia a dia

1. Rode `buildPlugin` e pegue o zip em `build/distributions/claude-commit-message-0.1.0.zip`.
2. No PyCharm: **Settings > Plugins > ⚙ > Install Plugin from Disk...** e selecione o zip.
3. Reinicie. Vá em **Settings > Tools > Claude Commit Message** e cole a API key.

## Configuração (Settings > Tools > Claude Commit Message)

| Campo | Descrição |
|---|---|
| API key | Sua chave da Anthropic (guardada no keychain). |
| Modelo | `claude-opus-4-8` (padrão), `claude-sonnet-4-6`, `claude-haiku-4-5-...` ou outro id. |
| Idioma da mensagem | Idioma da mensagem gerada (ex: `pt-BR`, `en`). |
| Max tokens | Tamanho máximo da resposta. |
| Instruções adicionais | Texto livre anexado ao prompt do sistema (ex: convenções do time). |

## Estrutura

```
src/main/java/com/bee2pay/commitclaude/
  GenerateCommitMessageAction.java  # o botão / ação
  DiffCollector.java                # Changes -> unified diff
  ClaudeClient.java                 # chamada HTTP à Messages API
  ClaudeSettings.java               # persistência (State + PasswordSafe)
  ClaudeSettingsConfigurable.java   # tela de Settings
src/main/resources/META-INF/plugin.xml
```

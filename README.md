<p align="center">
  <img src="src/main/resources/META-INF/pluginIcon.svg" width="96" alt="Claude Commit Message logo" />
</p>

<h1 align="center">Claude Commit Message</h1>

<p align="center">PyCharm / IntelliJ plugin that writes your Git commit message with Claude.</p>

---

Adds a button to the **commit message** toolbar (the same spot where the *AI Assistant*
button shows up). On click, the plugin grabs the diff of the commit's files, sends it to
**Claude**, and writes the generated commit message into the field.

## How it works

1. The button is registered in the `Vcs.MessageActionGroup` action group — the little icon
   bar next to the commit message field.
2. On click, it collects the `Change`s (the selected changes, or the default changelist if
   nothing is selected) and builds a *unified diff* using the same engine as "Create Patch".
3. It sends the diff to Claude — either through the **Claude Code CLI** (reusing your existing
   login) or via a `POST` to `https://api.anthropic.com/v1/messages` with an API key.
4. The returned text goes straight into the commit field.

## Requirements

- IntelliJ IDEA or PyCharm **2024.2+** (to open/build the project — it ships its own JDK).
- Either the **Claude Code CLI** installed and logged in, or an **Anthropic API key**
  (https://console.anthropic.com/).

> You don't need Java/Gradle installed on your system: when you open the project in IntelliJ,
> it uses the bundled JBR and provisions JDK 21 through the toolchain automatically.

## Build & run (development)

Open the folder in IntelliJ IDEA as a Gradle project. Then, in the **Gradle** tab:

- **`runIde`** — launches a sandbox PyCharm Community instance with the plugin installed.
  
- Open any project with Git, go to the commit view, and the button (Claude icon) shows up
  next to the message field.
- **`buildPlugin`** — produces the distributable `.zip` in `build/distributions/`.

From the command line (with Java 21 + Gradle, or after generating the wrapper with
`gradle wrapper`):

```bash
./gradlew runIde        # run in a sandbox
./gradlew buildPlugin   # build the zip
```

## Install into your everyday PyCharm

1. Run `buildPlugin` and grab the zip at `build/distributions/claude-commit-message-0.1.0.zip`.
2. In PyCharm: **Settings > Plugins > ⚙ > Install Plugin from Disk...** and pick the zip.
3. Restart. Go to **Settings > Tools > Claude Commit Message** and configure the login.

## Login

There are two authentication modes (under **Settings > Tools > Claude Commit Message > Login**):

- **Claude Code (CLI) — default:** reuses the login you already did with Claude Code in your
  terminal. No API key needed. The plugin calls `claude -p` and feeds the diff as the prompt.
  Use the **Test login** button to validate. Prerequisite: have Claude Code installed and
  logged in (run `claude` once in a terminal).
  - On Windows, the **CLI runtime** setting controls where it runs: `auto` (use a native
    `claude` on the `PATH`, otherwise fall back to WSL), `windows` (native cmd/PowerShell),
    or `wsl` (run inside WSL). Outside Windows it always uses the native shell.
- **Anthropic API key:** the classic mode, hitting `api.anthropic.com` directly. The key is
  stored in the IDE **PasswordSafe** (OS keychain), not in a config file.

## Configuration (Settings > Tools > Claude Commit Message)

| Field | Description |
|---|---|
| Login | `Claude Code (CLI)` (default) or `Anthropic API key`. |
| CLI executable | Name/path of the CLI binary (default `claude`). CLI mode only. |
| CLI runtime | On Windows: `auto` / `windows` / `wsl`. CLI mode only. |
| API key | Your Anthropic key (stored in the keychain). API key mode only. |
| Model | `claude-opus-4-8` (default), `claude-sonnet-4-6`, `claude-haiku-4-5-...` or another id. |
| Message language | Language of the generated message (e.g. `pt-BR`, `en`). |
| Max tokens | Maximum response size (API key mode). |
| Extra instructions | Free text appended to the system prompt (e.g. team conventions). |

## Project layout

```
src/main/java/com/raphaelmoral/commitclaude/
  GenerateCommitMessageAction.java  # the button / action
  DiffCollector.java                # Changes -> unified diff
  Prompts.java                      # shared system/user prompts
  ClaudeClient.java                 # API key backend (HTTP Messages API)
  ClaudeCliClient.java              # CLI backend (claude -p, reuses your login)
  ClaudeSettings.java               # persistence (State + PasswordSafe)
  ClaudeSettingsConfigurable.java   # Settings screen
src/main/resources/icons/claude.svg # button icon
src/main/resources/META-INF/plugin.xml
```

## License

MIT — see [LICENSE](LICENSE). Developed by Raphael Moral Piazera.

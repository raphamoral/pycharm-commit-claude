package com.raphaelmoral.commitclaude;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configurações do plugin, persistidas a nível de aplicação.
 *
 * <p>O modelo / idioma / instruções ficam em {@link State} (gravado em claudeCommit.xml).
 * A API key NÃO fica no State — é guardada no PasswordSafe da IDE (keychain do SO).</p>
 */
@State(name = "ClaudeCommitSettings", storages = @Storage("claudeCommit.xml"))
@Service(Service.Level.APP)
public final class ClaudeSettings implements PersistentStateComponent<ClaudeSettings.State> {

    public static final class State {
        /** "cli" = usa a CLI do Claude Code (login do WSL); "api" = usa a API key da Anthropic. */
        public String authMode = "cli";
        /** Executável da CLI do Claude Code (normalmente "claude"). */
        public String cliExecutable = "claude";
        public String model = "claude-opus-4-8";
        public int maxTokens = 1024;
        public String language = "pt-BR";
        public String customInstructions = "";
    }

    private State state = new State();

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State s) {
        this.state = s;
    }

    public static ClaudeSettings getInstance() {
        return ApplicationManager.getApplication().getService(ClaudeSettings.class);
    }

    private static CredentialAttributes credentialAttributes() {
        return new CredentialAttributes(
                CredentialAttributesKt.generateServiceName("ClaudeCommit", "anthropic-api-key"));
    }

    public @NotNull String getApiKey() {
        String pwd = PasswordSafe.getInstance().getPassword(credentialAttributes());
        return pwd == null ? "" : pwd;
    }

    public void setApiKey(@Nullable String key) {
        PasswordSafe.getInstance().setPassword(credentialAttributes(), key);
    }
}

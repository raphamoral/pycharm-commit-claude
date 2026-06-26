package com.raphaelmoral.commitclaude;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;

/**
 * Tela de configuração em Settings > Tools > Claude Commit Message.
 */
public class ClaudeSettingsConfigurable implements Configurable {

    private static final String[] MODELS = {
            "claude-opus-4-8",
            "claude-sonnet-4-6",
            "claude-haiku-4-5-20251001",
    };

    // Idiomas sugeridos (ISO 639-1 + variantes de locale comuns). O combo é editável,
    // então qualquer outro valor também é aceito. "en-US" (default) e "pt-BR" ficam no topo
    // por serem os mais usados; o restante segue em ordem alfabética de código.
    private static final String[] LANGUAGES = {
            "en-US",
            "pt-BR",
            "en",
            "af",     // Afrikaans
            "am",     // Amárico
            "ar",     // Árabe
            "az",     // Azerbaijano
            "be",     // Bielorrusso
            "bg",     // Búlgaro
            "bn",     // Bengali
            "bs",     // Bósnio
            "ca",     // Catalão
            "cs",     // Tcheco
            "cy",     // Galês
            "da",     // Dinamarquês
            "de",     // Alemão
            "de-AT",  // Alemão (Áustria)
            "de-CH",  // Alemão (Suíça)
            "el",     // Grego
            "en-AU",  // Inglês (Austrália)
            "en-CA",  // Inglês (Canadá)
            "en-GB",  // Inglês (Reino Unido)
            "en-US",  // Inglês (EUA)
            "eo",     // Esperanto
            "es",     // Espanhol
            "es-419", // Espanhol (América Latina)
            "es-MX",  // Espanhol (México)
            "et",     // Estoniano
            "eu",     // Basco
            "fa",     // Persa
            "fi",     // Finlandês
            "fil",    // Filipino
            "fr",     // Francês
            "fr-CA",  // Francês (Canadá)
            "ga",     // Irlandês
            "gl",     // Galego
            "gu",     // Guzerate
            "he",     // Hebraico
            "hi",     // Híndi
            "hr",     // Croata
            "hu",     // Húngaro
            "hy",     // Armênio
            "id",     // Indonésio
            "is",     // Islandês
            "it",     // Italiano
            "ja",     // Japonês
            "ka",     // Georgiano
            "kk",     // Cazaque
            "km",     // Khmer
            "kn",     // Canarês
            "ko",     // Coreano
            "lo",     // Laosiano
            "lt",     // Lituano
            "lv",     // Letão
            "mk",     // Macedônio
            "ml",     // Malaiala
            "mn",     // Mongol
            "mr",     // Marata
            "ms",     // Malaio
            "my",     // Birmanês
            "ne",     // Nepalês
            "nl",     // Holandês
            "no",     // Norueguês
            "pa",     // Panjabi
            "pl",     // Polonês
            "pt",     // Português
            "pt-PT",  // Português (Portugal)
            "ro",     // Romeno
            "ru",     // Russo
            "si",     // Cingalês
            "sk",     // Eslovaco
            "sl",     // Esloveno
            "sq",     // Albanês
            "sr",     // Sérvio
            "sv",     // Sueco
            "sw",     // Suaíli
            "ta",     // Tâmil
            "te",     // Télugo
            "th",     // Tailandês
            "tr",     // Turco
            "uk",     // Ucraniano
            "ur",     // Urdu
            "uz",     // Uzbeque
            "vi",     // Vietnamita
            "zh",     // Chinês
            "zh-Hans",// Chinês (simplificado)
            "zh-Hant",// Chinês (tradicional)
            "zu",     // Zulu
    };

    private static final String AUTH_CLI_LABEL = "Claude Code (CLI — sem API key)";
    private static final String AUTH_API_LABEL = "API key da Anthropic";

    // Ambiente da CLI no Windows: rótulo exibido <-> valor salvo em State.cliRuntime.
    private static final String RUNTIME_AUTO_LABEL = "Automático (detecta Windows ou WSL)";
    private static final String RUNTIME_WINDOWS_LABEL = "Windows nativo (cmd/PowerShell)";
    private static final String RUNTIME_WSL_LABEL = "WSL";

    private ComboBox<String> authModeCombo;
    private JBTextField cliExecutableField;
    private ComboBox<String> cliRuntimeCombo;
    private JBTextField gitBashPathField;
    private JButton testButton;
    private JBPasswordField apiKeyField;
    private ComboBox<String> modelCombo;
    private ComboBox<String> languageCombo;
    private JBTextField maxTokensField;
    private JTextArea customInstructionsArea;
    private JPanel panel;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Claude Commit Message";
    }

    @Override
    public @Nullable JComponent createComponent() {
        authModeCombo = new ComboBox<>(new String[]{AUTH_CLI_LABEL, AUTH_API_LABEL});
        cliExecutableField = new JBTextField();
        cliRuntimeCombo = new ComboBox<>(new String[]{
                RUNTIME_AUTO_LABEL, RUNTIME_WINDOWS_LABEL, RUNTIME_WSL_LABEL});
        gitBashPathField = new JBTextField();
        testButton = new JButton("Testar login");
        testButton.addActionListener(e -> runCliTest());

        apiKeyField = new JBPasswordField();
        modelCombo = new ComboBox<>(MODELS);
        modelCombo.setEditable(true);
        languageCombo = new ComboBox<>(LANGUAGES);
        languageCombo.setEditable(true);
        maxTokensField = new JBTextField();
        customInstructionsArea = new JTextArea(5, 40);
        customInstructionsArea.setLineWrap(true);
        customInstructionsArea.setWrapStyleWord(true);

        JPanel cliRow = new JPanel(new BorderLayout(8, 0));
        cliRow.add(cliExecutableField, BorderLayout.CENTER);
        cliRow.add(testButton, BorderLayout.EAST);

        authModeCombo.addActionListener(e -> updateEnablement());
        cliRuntimeCombo.addActionListener(e -> updateEnablement());

        FormBuilder builder = FormBuilder.createFormBuilder()
                .addLabeledComponent("Login:", authModeCombo, 1, false)
                .addLabeledComponent("Executável da CLI:", cliRow, 1, false);
        // O ambiente da CLI só faz diferença quando o PyCharm roda no Windows.
        if (SystemInfo.isWindows) {
            builder.addLabeledComponent("Ambiente da CLI:", cliRuntimeCombo, 1, false);
            builder.addLabeledComponent("git-bash (Windows nativo):", gitBashPathField, 1, false);
            builder.addComponent(new com.intellij.ui.components.JBLabel(
                    "Opcional. Vazio = detecta automaticamente. Ex.: C:\\Program Files\\Git\\bin\\bash.exe",
                    com.intellij.util.ui.UIUtil.ComponentStyle.SMALL,
                    com.intellij.util.ui.UIUtil.FontColor.BRIGHTER));
        }
        panel = builder
                .addLabeledComponent("API key (Anthropic):", apiKeyField, 1, false)
                .addLabeledComponent("Modelo:", modelCombo, 1, false)
                .addLabeledComponent("Idioma da mensagem:", languageCombo, 1, false)
                .addLabeledComponent("Max tokens:", maxTokensField, 1, false)
                .addLabeledComponent("Instruções adicionais:", new JScrollPane(customInstructionsArea), 1, true)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        panel.setBorder(JBUI.Borders.empty(10));

        reset();
        return panel;
    }

    /** Habilita/desabilita campos conforme o modo de login selecionado. */
    private void updateEnablement() {
        boolean cli = AUTH_CLI_LABEL.equals(authModeCombo.getItem());
        cliExecutableField.setEnabled(cli);
        cliRuntimeCombo.setEnabled(cli);
        gitBashPathField.setEnabled(cli);
        testButton.setEnabled(cli);
        apiKeyField.setEnabled(!cli);
    }

    private String selectedCliRuntime() {
        Object item = cliRuntimeCombo.getItem();
        if (RUNTIME_WINDOWS_LABEL.equals(item)) {
            return "windows";
        }
        if (RUNTIME_WSL_LABEL.equals(item)) {
            return "wsl";
        }
        return "auto";
    }

    private static String runtimeLabel(String value) {
        if ("windows".equals(value)) {
            return RUNTIME_WINDOWS_LABEL;
        }
        if ("wsl".equals(value)) {
            return RUNTIME_WSL_LABEL;
        }
        return RUNTIME_AUTO_LABEL;
    }

    /** Roda um ping na CLI em background e mostra o resultado, sem travar a UI. */
    private void runCliTest() {
        ClaudeSettings.State probe = new ClaudeSettings.State();
        probe.model = String.valueOf(modelCombo.getItem()).trim();
        probe.cliExecutable = cliExecutableField.getText().trim();
        probe.cliRuntime = selectedCliRuntime();
        probe.gitBashPath = gitBashPathField.getText().trim();

        testButton.setEnabled(false);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            boolean ok;
            String message;
            try {
                String out = ClaudeCliClient.run("Responda somente com a palavra: OK", probe);
                ok = true;
                message = "Login OK! Resposta da CLI:\n\n" + out;
            } catch (Exception ex) {
                ok = false;
                message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            }
            boolean finalOk = ok;
            String finalMessage = message;
            ApplicationManager.getApplication().invokeLater(() -> {
                testButton.setEnabled(true);
                if (finalOk) {
                    Messages.showInfoMessage(panel, finalMessage, "Teste da CLI do Claude");
                } else {
                    Messages.showErrorDialog(panel, finalMessage, "Teste da CLI do Claude");
                }
            });
        });
    }

    private String selectedAuthMode() {
        return AUTH_API_LABEL.equals(authModeCombo.getItem()) ? "api" : "cli";
    }

    @Override
    public boolean isModified() {
        ClaudeSettings s = ClaudeSettings.getInstance();
        ClaudeSettings.State st = s.getState();
        return !selectedAuthMode().equals(st.authMode)
                || !cliExecutableField.getText().equals(st.cliExecutable)
                || !selectedCliRuntime().equals(st.cliRuntime)
                || !gitBashPathField.getText().equals(st.gitBashPath == null ? "" : st.gitBashPath)
                || !new String(apiKeyField.getPassword()).equals(s.getApiKey())
                || !String.valueOf(modelCombo.getItem()).equals(st.model)
                || !String.valueOf(languageCombo.getItem()).equals(st.language)
                || !maxTokensField.getText().equals(String.valueOf(st.maxTokens))
                || !customInstructionsArea.getText().equals(st.customInstructions);
    }

    @Override
    public void apply() {
        ClaudeSettings s = ClaudeSettings.getInstance();
        ClaudeSettings.State st = s.getState();
        st.authMode = selectedAuthMode();
        st.cliExecutable = cliExecutableField.getText().trim();
        st.cliRuntime = selectedCliRuntime();
        st.gitBashPath = gitBashPathField.getText().trim();
        s.setApiKey(new String(apiKeyField.getPassword()));
        st.model = String.valueOf(modelCombo.getItem()).trim();
        st.language = String.valueOf(languageCombo.getItem()).trim();
        try {
            st.maxTokens = Integer.parseInt(maxTokensField.getText().trim());
        } catch (NumberFormatException ignored) {
            // mantém o valor anterior se o usuário digitar algo inválido
        }
        st.customInstructions = customInstructionsArea.getText();
    }

    @Override
    public void reset() {
        ClaudeSettings s = ClaudeSettings.getInstance();
        ClaudeSettings.State st = s.getState();
        authModeCombo.setItem("api".equals(st.authMode) ? AUTH_API_LABEL : AUTH_CLI_LABEL);
        cliExecutableField.setText(st.cliExecutable);
        cliRuntimeCombo.setItem(runtimeLabel(st.cliRuntime));
        gitBashPathField.setText(st.gitBashPath == null ? "" : st.gitBashPath);
        apiKeyField.setText(s.getApiKey());
        modelCombo.setItem(st.model);
        languageCombo.setItem(st.language);
        maxTokensField.setText(String.valueOf(st.maxTokens));
        customInstructionsArea.setText(st.customInstructions);
        updateEnablement();
    }
}

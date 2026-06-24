package com.raphaelmoral.commitclaude;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
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

    private static final String AUTH_CLI_LABEL = "Claude Code (login do WSL — sem API key)";
    private static final String AUTH_API_LABEL = "API key da Anthropic";

    private ComboBox<String> authModeCombo;
    private JBTextField cliExecutableField;
    private JButton testButton;
    private JBPasswordField apiKeyField;
    private ComboBox<String> modelCombo;
    private JBTextField languageField;
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
        testButton = new JButton("Testar login");
        testButton.addActionListener(e -> runCliTest());

        apiKeyField = new JBPasswordField();
        modelCombo = new ComboBox<>(MODELS);
        modelCombo.setEditable(true);
        languageField = new JBTextField();
        maxTokensField = new JBTextField();
        customInstructionsArea = new JTextArea(5, 40);
        customInstructionsArea.setLineWrap(true);
        customInstructionsArea.setWrapStyleWord(true);

        JPanel cliRow = new JPanel(new BorderLayout(8, 0));
        cliRow.add(cliExecutableField, BorderLayout.CENTER);
        cliRow.add(testButton, BorderLayout.EAST);

        authModeCombo.addActionListener(e -> updateEnablement());

        panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Login:", authModeCombo, 1, false)
                .addLabeledComponent("Executável da CLI:", cliRow, 1, false)
                .addLabeledComponent("API key (Anthropic):", apiKeyField, 1, false)
                .addLabeledComponent("Modelo:", modelCombo, 1, false)
                .addLabeledComponent("Idioma da mensagem:", languageField, 1, false)
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
        testButton.setEnabled(cli);
        apiKeyField.setEnabled(!cli);
    }

    /** Roda um ping na CLI em background e mostra o resultado, sem travar a UI. */
    private void runCliTest() {
        ClaudeSettings.State probe = new ClaudeSettings.State();
        probe.model = String.valueOf(modelCombo.getItem()).trim();
        probe.cliExecutable = cliExecutableField.getText().trim();

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
                || !new String(apiKeyField.getPassword()).equals(s.getApiKey())
                || !String.valueOf(modelCombo.getItem()).equals(st.model)
                || !languageField.getText().equals(st.language)
                || !maxTokensField.getText().equals(String.valueOf(st.maxTokens))
                || !customInstructionsArea.getText().equals(st.customInstructions);
    }

    @Override
    public void apply() {
        ClaudeSettings s = ClaudeSettings.getInstance();
        ClaudeSettings.State st = s.getState();
        st.authMode = selectedAuthMode();
        st.cliExecutable = cliExecutableField.getText().trim();
        s.setApiKey(new String(apiKeyField.getPassword()));
        st.model = String.valueOf(modelCombo.getItem()).trim();
        st.language = languageField.getText().trim();
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
        apiKeyField.setText(s.getApiKey());
        modelCombo.setItem(st.model);
        languageField.setText(st.language);
        maxTokensField.setText(String.valueOf(st.maxTokens));
        customInstructionsArea.setText(st.customInstructions);
        updateEnablement();
    }
}

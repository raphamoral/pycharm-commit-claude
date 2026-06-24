package com.bee2pay.commitclaude;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Tela de configuração em Settings > Tools > Claude Commit Message.
 */
public class ClaudeSettingsConfigurable implements Configurable {

    private static final String[] MODELS = {
            "claude-opus-4-8",
            "claude-sonnet-4-6",
            "claude-haiku-4-5-20251001",
    };

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
        apiKeyField = new JBPasswordField();
        modelCombo = new ComboBox<>(MODELS);
        modelCombo.setEditable(true);
        languageField = new JBTextField();
        maxTokensField = new JBTextField();
        customInstructionsArea = new JTextArea(5, 40);
        customInstructionsArea.setLineWrap(true);
        customInstructionsArea.setWrapStyleWord(true);

        panel = FormBuilder.createFormBuilder()
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

    @Override
    public boolean isModified() {
        ClaudeSettings s = ClaudeSettings.getInstance();
        ClaudeSettings.State st = s.getState();
        return !new String(apiKeyField.getPassword()).equals(s.getApiKey())
                || !String.valueOf(modelCombo.getItem()).equals(st.model)
                || !languageField.getText().equals(st.language)
                || !maxTokensField.getText().equals(String.valueOf(st.maxTokens))
                || !customInstructionsArea.getText().equals(st.customInstructions);
    }

    @Override
    public void apply() {
        ClaudeSettings s = ClaudeSettings.getInstance();
        ClaudeSettings.State st = s.getState();
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
        apiKeyField.setText(s.getApiKey());
        modelCombo.setItem(st.model);
        languageField.setText(st.language);
        maxTokensField.setText(String.valueOf(st.maxTokens));
        customInstructionsArea.setText(st.customInstructions);
    }
}

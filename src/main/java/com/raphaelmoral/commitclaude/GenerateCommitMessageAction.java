package com.raphaelmoral.commitclaude;

import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.CommitMessageI;
import com.intellij.ui.AnimatedIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Botão na barra da mensagem de commit (grupo {@code Vcs.MessageActionGroup} — o mesmo
 * onde fica o botão da AI Assistant). Coleta o diff dos arquivos do commit, chama a
 * Claude e escreve o resultado no campo de mensagem.
 */
public class GenerateCommitMessageAction extends AnAction {

    private static final String TITLE = "Claude Commit Message";

    private static final Icon CLAUDE_ICON =
            IconLoader.getIcon("/icons/claude.svg", GenerateCommitMessageAction.class);

    private static final Icon SPINNER = AnimatedIcon.Default.INSTANCE;

    /** true enquanto a geração está rodando — troca o ícone por um spinner e desabilita o botão. */
    private volatile boolean running = false;

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        if (running) {
            presentation.setIcon(SPINNER);
            presentation.setDisabledIcon(SPINNER);
            presentation.setVisible(true);
            presentation.setEnabled(false);
            return;
        }
        presentation.setIcon(CLAUDE_ICON);
        boolean available = e.getProject() != null
                && e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) != null;
        presentation.setEnabledAndVisible(available);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        CommitMessageI commitMessageControl =
                (CommitMessageI) e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL);
        if (commitMessageControl == null) {
            return;
        }

        Collection<Change> changes = resolveChanges(e, project);
        if (changes.isEmpty()) {
            Messages.showInfoMessage(project,
                    "Nenhuma alteração encontrada para gerar a mensagem de commit.", TITLE);
            return;
        }

        new Task.Backgroundable(project, "Gerando mensagem de commit com Claude", true) {
            private String result;
            private Exception error;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    String diff = ReadAction.nonBlocking((Callable<String>) () -> {
                        try {
                            return DiffCollector.buildDiff(project, changes);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }).executeSynchronously();
                    if (diff == null || diff.isBlank()) {
                        throw new IllegalStateException("Não foi possível gerar o diff das alterações.");
                    }
                    ClaudeSettings settings = ClaudeSettings.getInstance();
                    ClaudeSettings.State st = settings.getState();
                    if ("api".equals(st.authMode)) {
                        result = ClaudeClient.generateCommitMessage(diff, st, settings.getApiKey());
                    } else {
                        result = ClaudeCliClient.generateCommitMessage(diff, st);
                    }
                    // Tira o code fence de markdown que o modelo às vezes adiciona
                    // apesar de instruído a não usar (cobre os dois backends).
                    result = Prompts.stripFences(result);
                } catch (Exception ex) {
                    error = ex;
                }
            }

            @Override
            public void onSuccess() {
                if (error != null) {
                    Messages.showErrorDialog(project,
                            error.getMessage() == null ? error.toString() : error.getMessage(), TITLE);
                    return;
                }
                if (result != null && !result.isBlank()) {
                    commitMessageControl.setCommitMessage(result);
                } else {
                    Messages.showWarningDialog(project, "A Claude retornou uma resposta vazia.", TITLE);
                }
            }

            @Override
            public void onFinished() {
                running = false;
                ActivityTracker.getInstance().inc();
            }
        }.queue();

        running = true;
        ActivityTracker.getInstance().inc();
    }

    /**
     * Usa as alterações selecionadas na árvore de commit; se nada estiver selecionado,
     * cai para todas as alterações da changelist padrão.
     */
    private static Collection<Change> resolveChanges(AnActionEvent e, Project project) {
        Change[] selected = e.getData(VcsDataKeys.CHANGES);
        if (selected != null && selected.length > 0) {
            return Arrays.asList(selected);
        }
        Collection<Change> defaultChanges =
                ChangeListManager.getInstance(project).getDefaultChangeList().getChanges();
        return defaultChanges != null ? defaultChanges : List.of();
    }
}

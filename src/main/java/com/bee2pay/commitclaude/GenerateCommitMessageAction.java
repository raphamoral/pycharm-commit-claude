package com.bee2pay.commitclaude;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.ui.CommitMessageI;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Botão na barra da mensagem de commit (grupo {@code Vcs.MessageActionGroup} — o mesmo
 * onde fica o botão da AI Assistant). Coleta o diff dos arquivos do commit, chama a
 * Claude e escreve o resultado no campo de mensagem.
 */
public class GenerateCommitMessageAction extends AnAction {

    private static final String TITLE = "Claude Commit Message";

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean available = e.getProject() != null
                && e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) != null;
        e.getPresentation().setEnabledAndVisible(available);
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
                    String diff = ReadAction.compute(() -> {
                        try {
                            return DiffCollector.buildDiff(project, changes);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    });
                    if (diff == null || diff.isBlank()) {
                        throw new IllegalStateException("Não foi possível gerar o diff das alterações.");
                    }
                    ClaudeSettings settings = ClaudeSettings.getInstance();
                    result = ClaudeClient.generateCommitMessage(diff, settings.getState(), settings.getApiKey());
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
        }.queue();
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

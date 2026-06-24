package com.raphaelmoral.commitclaude;

/**
 * Prompts compartilhados entre os dois backends (API HTTP e CLI do Claude Code).
 */
final class Prompts {

    private Prompts() {
    }

    static String system(ClaudeSettings.State s) {
        StringBuilder sb = new StringBuilder();
        sb.append("Você é um assistente que escreve mensagens de commit Git claras e idiomáticas. ");
        sb.append("Analise o diff fornecido e gere UMA mensagem de commit. ");
        sb.append("Use o formato Conventional Commits (tipo: descrição) quando fizer sentido. ");
        sb.append("A primeira linha deve ter no máximo 72 caracteres, no modo imperativo. ");
        sb.append("Se houver detalhes relevantes, adicione um corpo após uma linha em branco. ");
        sb.append("Responda APENAS com a mensagem de commit — sem aspas, sem markdown, sem explicações. ");
        sb.append("Idioma da mensagem: ").append(s.language).append(". ");
        if (s.customInstructions != null && !s.customInstructions.isBlank()) {
            sb.append("Instruções adicionais: ").append(s.customInstructions);
        }
        return sb.toString();
    }

    static String user(String diff) {
        return "Gere a mensagem de commit para o seguinte diff (formato unified diff):\n\n" + diff;
    }
}

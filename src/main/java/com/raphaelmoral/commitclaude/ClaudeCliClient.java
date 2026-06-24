package com.raphaelmoral.commitclaude;

import com.intellij.openapi.util.SystemInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Gera a mensagem de commit chamando a CLI do Claude Code ({@code claude -p}), reaproveitando
 * o login já feito no WSL/terminal — sem precisar de API key.
 *
 * <p>O prompt (instruções + diff) é enviado pelo <em>stdin</em>, evitando problemas de
 * escape e de tamanho de linha de comando. Em PyCharm no Windows, a CLI é invocada dentro
 * do WSL ({@code wsl.exe bash -lc 'claude -p'}); em PyCharm rodando no próprio Linux/WSL,
 * é chamada direto via {@code bash -lc}.</p>
 */
public final class ClaudeCliClient {

    private static final int MAX_DIFF_CHARS = 100_000;
    private static final long TIMEOUT_SECONDS = 180;

    private ClaudeCliClient() {
    }

    public static String generateCommitMessage(String diff, ClaudeSettings.State settings) throws Exception {
        String trimmedDiff = diff.length() > MAX_DIFF_CHARS
                ? diff.substring(0, MAX_DIFF_CHARS) + "\n\n[... diff truncado ...]"
                : diff;
        String prompt = Prompts.system(settings) + "\n\n" + Prompts.user(trimmedDiff);
        return run(prompt, settings);
    }

    /** Roda a CLI com o prompt vindo do stdin e devolve o stdout. */
    static String run(String prompt, ClaudeSettings.State settings) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(buildCommand(settings));
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível iniciar a CLI do Claude ("
                    + String.join(" ", pb.command()) + "): " + e.getMessage()
                    + "\nVerifique se o Claude Code está instalado e se o comando está correto em Settings.", e);
        }

        // Lê stderr em paralelo para o pipe não encher e travar o processo.
        StringBuilder err = new StringBuilder();
        Thread errReader = new Thread(() -> {
            try {
                err.append(readAll(process.getErrorStream()));
            } catch (IOException ignored) {
            }
        }, "claude-cli-stderr");
        errReader.setDaemon(true);
        errReader.start();

        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        }

        String stdout = readAll(process.getInputStream());

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Tempo esgotado (" + TIMEOUT_SECONDS + "s) ao chamar a CLI do Claude.");
        }
        errReader.join(2000);

        int exit = process.exitValue();
        if (exit != 0) {
            String detail = !err.toString().isBlank() ? err.toString() : stdout;
            throw new RuntimeException("A CLI do Claude retornou código " + exit + ":\n" + detail.trim()
                    + "\n\nDica: rode 'claude' uma vez no terminal do WSL para confirmar que está logado.");
        }
        return stdout.trim();
    }

    private static List<String> buildCommand(ClaudeSettings.State s) {
        String exe = (s.cliExecutable == null || s.cliExecutable.isBlank()) ? "claude" : s.cliExecutable.trim();
        StringBuilder inner = new StringBuilder(exe).append(" -p");
        if (s.model != null && !s.model.isBlank()) {
            inner.append(" --model ").append(s.model.trim());
        }

        List<String> command = new ArrayList<>();
        if (SystemInfo.isWindows) {
            // PyCharm no Windows: a CLI (e o login) vivem dentro do WSL.
            command.add("wsl.exe");
            command.add("bash");
            command.add("-lc");
            command.add(inner.toString());
        } else {
            // PyCharm rodando no próprio Linux/WSL.
            command.add("bash");
            command.add("-lc");
            command.add(inner.toString());
        }
        return command;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }
}

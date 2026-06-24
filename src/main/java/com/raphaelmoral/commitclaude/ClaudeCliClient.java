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
 * escape e de tamanho de linha de comando. Em PyCharm no Windows, a CLI pode rodar
 * nativamente ({@code cmd.exe /c claude -p}, que também cobre quem instalou via PowerShell)
 * ou dentro do WSL ({@code wsl.exe bash -lc 'claude -p'}); o ambiente é escolhido pelo
 * setting {@code cliRuntime} ("auto" detecta qual está disponível). Em PyCharm rodando no
 * próprio Linux/macOS, é chamada direto via {@code bash -lc}.</p>
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
                    + "\n\nDica: rode 'claude' uma vez no terminal (Windows ou WSL, conforme o ambiente"
                    + " escolhido em Settings) para confirmar que está logado.");
        }
        return stdout.trim();
    }

    private static List<String> buildCommand(ClaudeSettings.State s) {
        String exe = (s.cliExecutable == null || s.cliExecutable.isBlank()) ? "claude" : s.cliExecutable.trim();
        List<String> args = new ArrayList<>();
        args.add(exe);
        args.add("-p");
        if (s.model != null && !s.model.isBlank()) {
            args.add("--model");
            args.add(s.model.trim());
        }

        if (!SystemInfo.isWindows) {
            // PyCharm rodando no próprio Linux/macOS (login shell carrega o PATH do Claude Code).
            return List.of("bash", "-lc", String.join(" ", args));
        }

        if ("windows".equals(resolveWindowsRuntime(s))) {
            // Claude Code instalado nativamente no Windows. O npm cria um shim "claude.cmd",
            // por isso passamos por cmd.exe /c (que também atende quem usa PowerShell — é o
            // mesmo executável). Os args não contêm conteúdo do usuário (o prompt vai por stdin).
            List<String> command = new ArrayList<>();
            command.add("cmd.exe");
            command.add("/c");
            command.addAll(args);
            return command;
        }

        // WSL: a CLI (e o login) vivem dentro do WSL.
        return List.of("wsl.exe", "bash", "-lc", String.join(" ", args));
    }

    /**
     * Decide se, no Windows, a CLI roda nativamente ("windows") ou no WSL ("wsl").
     * No modo "auto", considera nativa se o executável estiver no PATH do Windows.
     */
    private static String resolveWindowsRuntime(ClaudeSettings.State s) {
        String mode = (s.cliRuntime == null || s.cliRuntime.isBlank()) ? "auto" : s.cliRuntime.trim();
        if ("windows".equals(mode) || "wsl".equals(mode)) {
            return mode;
        }
        String exe = (s.cliExecutable == null || s.cliExecutable.isBlank()) ? "claude" : s.cliExecutable.trim();
        return isOnWindowsPath(exe) ? "windows" : "wsl";
    }

    /** {@code where <exe>} no Windows: exit 0 quando o executável existe no PATH. */
    private static boolean isOnWindowsPath(String exe) {
        try {
            Process p = new ProcessBuilder("cmd.exe", "/c", "where", exe)
                    .redirectErrorStream(true)
                    .start();
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
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

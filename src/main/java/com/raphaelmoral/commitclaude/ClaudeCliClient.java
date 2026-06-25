package com.raphaelmoral.commitclaude;

import com.intellij.openapi.util.SystemInfo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /**
     * Roda a CLI com o prompt vindo do stdin e devolve o stdout.
     *
     * <p>Fora do Windows há um único ambiente (shell nativo). No Windows, se o usuário fixou
     * o ambiente ({@code "windows"} ou {@code "wsl"}) ele é respeitado; no modo {@code "auto"}
     * tentamos os ambientes em ordem de preferência e caímos para o próximo quando um falha.
     * O ambiente que funcionou vira o default ({@code settings.cliRuntime}). Só quando todos
     * falham é que devolvemos o erro de "não está logado".</p>
     */
    static String run(String prompt, ClaudeSettings.State settings) throws Exception {
        if (!SystemInfo.isWindows) {
            return runOnce(prompt, settings, "unix");
        }
        String mode = normalizeRuntime(settings.cliRuntime);
        if (!"auto".equals(mode)) {
            // Usuário fixou o ambiente em Settings — respeita e não tenta o outro.
            return runOnce(prompt, settings, mode);
        }
        List<String> failures = new ArrayList<>();
        for (String runtime : autoOrder(settings)) {
            try {
                String out = runOnce(prompt, settings, runtime);
                settings.cliRuntime = runtime; // grava o default que funcionou
                return out;
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                failures.add("• " + runtimeName(runtime) + ":\n" + msg.trim());
            }
        }
        throw new RuntimeException("Você não está logado na CLI do Claude em nenhum ambiente "
                + "(Windows nativo nem WSL).\n\n" + String.join("\n\n", failures)
                + "\n\nRode 'claude' uma vez no terminal (Windows ou WSL) para logar, "
                + "ou selecione o ambiente certo em Settings > Tools > Claude Commit Message.");
    }

    /** Uma tentativa em um ambiente específico ({@code "unix"}, {@code "windows"} ou {@code "wsl"}). */
    private static String runOnce(String prompt, ClaudeSettings.State settings, String runtime) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(buildCommand(settings, runtime));
        // Rodando nativamente no Windows, o Claude Code precisa do git-bash apontado por
        // CLAUDE_CODE_GIT_BASH_PATH. Preenchemos automaticamente (ou com o caminho do setting).
        if ("windows".equals(runtime)) {
            configureGitBash(pb.environment(), settings);
        }
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
            throw new RuntimeException("A CLI do Claude retornou código " + exit + ":\n" + detail.trim());
        }
        return stdout.trim();
    }

    private static List<String> buildCommand(ClaudeSettings.State s, String runtime) {
        String exe = (s.cliExecutable == null || s.cliExecutable.isBlank()) ? "claude" : s.cliExecutable.trim();
        List<String> args = new ArrayList<>();
        args.add(exe);
        args.add("-p");
        if (s.model != null && !s.model.isBlank()) {
            args.add("--model");
            args.add(s.model.trim());
        }

        if ("windows".equals(runtime)) {
            // Claude Code instalado nativamente no Windows. O npm cria um shim "claude.cmd",
            // por isso passamos por cmd.exe /c (que também atende quem usa PowerShell — é o
            // mesmo executável). Os args não contêm conteúdo do usuário (o prompt vai por stdin).
            List<String> command = new ArrayList<>();
            command.add("cmd.exe");
            command.add("/c");
            command.addAll(args);
            return command;
        }
        if ("wsl".equals(runtime)) {
            // WSL: a CLI (e o login) vivem dentro do WSL.
            return List.of("wsl.exe", "bash", "-lc", String.join(" ", args));
        }
        // "unix": PyCharm rodando no próprio Linux/macOS (login shell carrega o PATH do Claude Code).
        return List.of("bash", "-lc", String.join(" ", args));
    }

    /** Normaliza o setting para {@code "auto"}, {@code "windows"} ou {@code "wsl"}. */
    private static String normalizeRuntime(String value) {
        String mode = (value == null || value.isBlank()) ? "auto" : value.trim();
        return ("windows".equals(mode) || "wsl".equals(mode)) ? mode : "auto";
    }

    /**
     * Ordem de tentativa no modo "auto": começa pelo ambiente mais provável (nativo se o
     * executável está no PATH do Windows, senão WSL) e usa o outro como fallback.
     */
    private static List<String> autoOrder(ClaudeSettings.State s) {
        String exe = (s.cliExecutable == null || s.cliExecutable.isBlank()) ? "claude" : s.cliExecutable.trim();
        return isOnWindowsPath(exe) ? List.of("windows", "wsl") : List.of("wsl", "windows");
    }

    private static String runtimeName(String runtime) {
        return "windows".equals(runtime) ? "Windows nativo (cmd/PowerShell)" : "WSL";
    }

    /**
     * Garante {@code CLAUDE_CODE_GIT_BASH_PATH} no ambiente do processo quando a CLI roda
     * nativamente no Windows. Ordem de prioridade:
     * <ol>
     *   <li>caminho informado no setting {@code gitBashPath} (se existir o arquivo);</li>
     *   <li>variável já presente no ambiente herdado (não sobrescreve);</li>
     *   <li>autodetecção nos locais padrão de instalação do Git for Windows.</li>
     * </ol>
     * Se nada for encontrado, deixa como está — a própria CLI emite a mensagem orientando
     * a instalar o git-bash ou definir a variável.
     */
    private static void configureGitBash(Map<String, String> env, ClaudeSettings.State s) {
        String override = (s.gitBashPath == null) ? "" : s.gitBashPath.trim();
        if (!override.isEmpty()) {
            if (new File(override).isFile()) {
                env.put("CLAUDE_CODE_GIT_BASH_PATH", override);
            }
            return;
        }
        if (env.containsKey("CLAUDE_CODE_GIT_BASH_PATH")
                && !env.get("CLAUDE_CODE_GIT_BASH_PATH").isBlank()) {
            return;
        }
        String detected = detectGitBash();
        if (detected != null) {
            env.put("CLAUDE_CODE_GIT_BASH_PATH", detected);
        }
    }

    /** Procura o {@code bash.exe} do Git for Windows nos caminhos usuais; null se não achar. */
    private static String detectGitBash() {
        List<String> candidates = new ArrayList<>();
        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        String localAppData = System.getenv("LOCALAPPDATA");
        if (programFiles != null) {
            candidates.add(programFiles + "\\Git\\bin\\bash.exe");
        }
        if (programFilesX86 != null) {
            candidates.add(programFilesX86 + "\\Git\\bin\\bash.exe");
        }
        if (localAppData != null) {
            candidates.add(localAppData + "\\Programs\\Git\\bin\\bash.exe");
        }
        candidates.add("C:\\Program Files\\Git\\bin\\bash.exe");
        candidates.add("C:\\Program Files (x86)\\Git\\bin\\bash.exe");
        for (String path : candidates) {
            if (new File(path).isFile()) {
                return path;
            }
        }
        // Última tentativa: derivar a partir do git no PATH (.../Git/cmd/git.exe -> .../Git/bin/bash.exe).
        String fromGit = deriveBashFromGit();
        return fromGit;
    }

    /** Usa {@code where git} para localizar a instalação e derivar {@code bin\bash.exe}. */
    private static String deriveBashFromGit() {
        try {
            Process p = new ProcessBuilder("cmd.exe", "/c", "where", "git")
                    .redirectErrorStream(true)
                    .start();
            String out;
            try (InputStream in = p.getInputStream()) {
                out = readAll(in);
            }
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            if (p.exitValue() != 0) {
                return null;
            }
            for (String line : out.split("\\r?\\n")) {
                String gitExe = line.trim();
                if (gitExe.isEmpty()) {
                    continue;
                }
                // Ex.: C:\Program Files\Git\cmd\git.exe -> C:\Program Files\Git\bin\bash.exe
                File cmdDir = new File(gitExe).getParentFile();
                if (cmdDir == null) {
                    continue;
                }
                File gitRoot = cmdDir.getParentFile();
                if (gitRoot == null) {
                    continue;
                }
                File bash = new File(gitRoot, "bin\\bash.exe");
                if (bash.isFile()) {
                    return bash.getPath();
                }
            }
            return null;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
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

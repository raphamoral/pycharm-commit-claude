package com.raphaelmoral.commitclaude;

import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder;
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;

import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Converte um conjunto de {@link Change} (arquivos do commit) em texto unified diff,
 * usando a mesma engine de patch que a IDE usa em "Create Patch".
 */
public final class DiffCollector {

    private DiffCollector() {
    }

    public static String buildDiff(Project project, Collection<Change> changes) throws Exception {
        if (changes == null || changes.isEmpty()) {
            return "";
        }
        String basePath = project.getBasePath();
        Path base = basePath != null ? Path.of(basePath) : Path.of(".");

        List<FilePatch> patches = IdeaTextPatchBuilder.buildPatch(
                project, changes, base, /* reversePatch = */ false, /* honorExcludedFromCommit = */ true);

        StringWriter writer = new StringWriter();
        UnifiedDiffWriter.write(project, patches, writer, "\n", null);
        return writer.toString();
    }
}

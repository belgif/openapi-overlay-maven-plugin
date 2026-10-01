package io.github.belgif.rest.openapi.overlay.maven.plugin;

import com.webfuzzing.overlayjvm.OverlayJVM;
import com.webfuzzing.overlayjvm.model.Overlay;
import org.codehaus.plexus.util.DirectoryScanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Resolves the OpenAPI documents to which one overlay is applied. */
public final class OverlayTargetResolver {
    private final Path inputDirectory;
    private final List<String> configuredPatterns;

    public OverlayTargetResolver(Path projectBase, Path inputDirectory, List<String> configuredPatterns) {
        Objects.requireNonNull(projectBase, "projectBase");
        this.inputDirectory = inputDirectory.toAbsolutePath().normalize();
        this.configuredPatterns = configuredPatterns == null ? List.of() : configuredPatterns;
    }

    public List<Path> resolve(Path overlay) throws IOException {
        Overlay model = OverlayJVM.parseOverlay(overlay.toFile());
        if (model.getExtends() != null && !model.getExtends().isBlank()) {
            Path extended = inputDirectory.resolve(model.getExtends()).normalize();
            if (!extended.startsWith(inputDirectory) || !Files.isRegularFile(extended)) {
                throw new IOException("Overlay extends file must be an existing file inside inputDirectory: "
                        + model.getExtends());
            }
            return List.of(extended);
        }
        if (!configuredPatterns.isEmpty()) {
            return findMatching(configuredPatterns);
        }
        return findMatching(List.of("**/*.json", "**/*.yaml", "**/*.yml"));
    }

    private List<Path> findMatching(List<String> patterns) {
        if (!Files.isDirectory(inputDirectory)) {
            return List.of();
        }
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(inputDirectory.toFile());
        scanner.setIncludes(patterns.stream()
                .map(pattern -> pattern.replace('\\', '/'))
                .toArray(String[]::new));
        scanner.scan();
        return Arrays.stream(scanner.getIncludedFiles())
                .map(inputDirectory::resolve)
                .filter(Files::isRegularFile)
                .map(path -> path.toAbsolutePath().normalize())
                .distinct()
                .toList();
    }
}




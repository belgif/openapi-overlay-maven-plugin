package io.github.belgif.rest.openapi.overlay.maven.plugin;

import org.codehaus.plexus.util.DirectoryScanner;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.model.Resource;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Stream;

/** Applies OpenAPI Overlay documents during Maven's generate-resources phase. */
@Mojo(name = "apply", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public class ApplyOverlayMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Explicit overlay files or glob patterns, relative to the project directory. */
    @Parameter
    private List<String> overlays;

    /** Directory scanned for overlays when {@code overlays} is not configured. */
    @Parameter(defaultValue = "${project.basedir}/src/main/openapi-overlay")
    private File overlayDirectory;

    /** Directory containing input OpenAPI documents. */
    @Parameter(defaultValue = "${project.basedir}/src/main/openapi")
    private File inputDirectory;

    /** Optional target files or glob patterns, relative to {@link #inputDirectory}. */
    @Parameter
    private List<String> inputFiles;

    @Parameter(defaultValue = "${project.build.directory}/generated-resources/openapi")
    private File outputDirectory;

    @Parameter(defaultValue = "false")
    private boolean addOutputToResources;

    @Override
    public void execute() throws MojoExecutionException {
        Path base = project.getBasedir().toPath().toAbsolutePath().normalize();
        try {
            List<Path> overlayFiles = resolveOverlays(base);
            Path inputRoot = inputDirectory.toPath().toAbsolutePath().normalize();
            OverlayTargetResolver resolver = new OverlayTargetResolver(base, inputRoot, inputFiles);
            OverlayApplier applier = new OverlayApplier();
            Files.createDirectories(outputDirectory.toPath());
            Map<Path, String> transformed = applyOverlays(overlayFiles, resolver, applier);
            for (Map.Entry<Path, String> entry : transformed.entrySet()) {
                Path output = outputPath(inputRoot, entry.getKey());
                Files.createDirectories(output.getParent());
                Files.writeString(output, entry.getValue(), StandardCharsets.UTF_8);
            }
            copyUntransformedInputs(inputRoot, transformed.keySet());
            if (overlayFiles.isEmpty()) {
                getLog().info("No OpenAPI overlays found; copied input documents unchanged.");
            }
            if (addOutputToResources) {
                Resource resource = new Resource();
                resource.setDirectory(outputDirectory.getAbsolutePath());
                project.addResource(resource);
            }
        } catch (IOException | RuntimeException e) {
            throw new MojoExecutionException("Unable to apply OpenAPI overlays", e);
        }
    }

    /**
     * Applies the given overlay files to their resolved target OpenAPI documents.
     * Returns a list of input files that were transformed, with their transformed content.
     */
    private Map<Path, String> applyOverlays(List<Path> overlayFiles, OverlayTargetResolver resolver, OverlayApplier applier) throws IOException {
        Map<Path, String> transformed = new LinkedHashMap<>();
        for (Path overlay : overlayFiles) {
            List<Path> targets = resolver.resolve(overlay);
            String overlayText = Files.readString(overlay, StandardCharsets.UTF_8);
            for (Path target : targets) {
                // multiple overlays may apply to the same target OpenAPI file, so we keep the transformation output in memory to apply subsequent overlays
                String current = transformed.computeIfAbsent(target,
                        path -> {
                            try {
                                return Files.readString(path, StandardCharsets.UTF_8);
                            } catch (IOException e) {
                                throw new OverlayIOException(e);
                            }
                        });
                transformed.put(target, applier.apply(overlayText, current));
                getLog().info("Applied " + overlay.getFileName() + " to " + target);
            }
        }
        return transformed;
    }

    private List<Path> resolveOverlays(Path base) throws IOException {
        if (overlays != null && !overlays.isEmpty()) {
            return resolvePatterns(base, overlays);
        }
        if (!overlayDirectory.isDirectory()) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(overlayDirectory.toPath())) {
            return paths.filter(Files::isRegularFile).filter(this::isOpenApiDocument)
                    .sorted().toList();
        }
    }

    private List<Path> resolvePatterns(Path base, List<String> patterns) {
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(base.toFile());
        scanner.setIncludes(patterns.stream()
                .map(pattern -> pattern.replace('\\', '/'))
                .toArray(String[]::new));
        scanner.scan();
        return Arrays.stream(scanner.getIncludedFiles())
                .map(base::resolve)
                .filter(Files::isRegularFile)
                .filter(this::isOpenApiDocument)
                .map(path -> path.toAbsolutePath().normalize())
                .distinct()
                .sorted()
                .toList();
    }

    private void copyUntransformedInputs(Path inputRoot, java.util.Set<Path> transformed) throws IOException {
        if (!Files.isDirectory(inputRoot)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(inputRoot)) {
            for (Path source : paths.filter(Files::isRegularFile).filter(this::isOpenApiDocument).toList()) {
                if (transformed.contains(source.toAbsolutePath().normalize())) {
                    continue;
                }
                Path output = outputPath(inputRoot, source);
                Files.createDirectories(output.getParent());
                Files.copy(source, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private Path outputPath(Path inputRoot, Path target) {
        Path relative;
        try {
            relative = inputRoot.relativize(target.toAbsolutePath().normalize());
        } catch (IllegalArgumentException e) {
            // shouldn't happen bc already checked that target is inside inputRoot
            throw new RuntimeException(
                    "Target and inputDirectory have incompatible filesystem roots: " + target, e);
        }
        if (relative.startsWith("..")) {
            // shouldn't happen bc already checked that target is inside inputRoot
            throw new RuntimeException("Target is outside inputDirectory: " + target);
        }

        return outputDirectory.toPath().toAbsolutePath().resolve(relative);
    }

    private boolean isOpenApiDocument(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml");
    }

    private static final class OverlayIOException extends RuntimeException {
        private OverlayIOException(IOException cause) {
            super(cause);
        }
    }
}








package io.github.belgif.rest.openapi.overlay.maven.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayTargetResolverTest {
    @TempDir
    Path temp;

    @Test
    void extendsTakesPriorityOverConfiguredTargets() throws Exception {
        Path extended = write("api.yaml", "openapi: 3.0.3\ninfo:\n  title: API\n  version: 1\n");
        Path configured = write("other.yaml", "openapi: 3.0.3\ninfo:\n  title: Other\n  version: 1\n");
        Path overlay = write("overlay.yaml", "overlay: 1.1.0\ninfo:\n  title: Overlay\n  version: 1\nextends: api.yaml\nactions:\n  - target: $.info.title\n    update: API\n");

        List<Path> result = new OverlayTargetResolver(temp, temp, List.of("other.yaml")).resolve(overlay);

        assertEquals(List.of(extended), result);
        assertTrue(Files.exists(configured));
    }

    @Test
    void extendsResolvesRelativeToInputDirectory() throws Exception {
        Path projectRootTarget = write("api.yaml", "openapi: 3.0.3\ninfo:\n  title: Project root\n  version: 1\n");
        Path inputTarget = write("input/api.yaml", "openapi: 3.0.3\ninfo:\n  title: Input directory\n  version: 1\n");
        Path overlay = write("overlays/overlay.yaml", "overlay: 1.1.0\ninfo:\n  title: Overlay\n  version: 1\nextends: api.yaml\nactions:\n  - target: $.info.title\n    update: Input directory\n");

        List<Path> result = new OverlayTargetResolver(temp, temp.resolve("input"), List.of()).resolve(overlay);

        assertEquals(List.of(inputTarget), result);
        assertTrue(Files.exists(projectRootTarget));
    }

    @Test
    void extendsOutsideInputDirectoryFails() throws Exception {
        write("outside.yaml", "openapi: 3.0.3\ninfo:\n  title: Outside\n  version: 1\n");
        Path inputDirectory = Files.createDirectory(temp.resolve("input"));
        Path overlay = write("overlay.yaml", "overlay: 1.1.0\ninfo:\n  title: Overlay\n  version: 1\nextends: ../outside.yaml\nactions:\n  - target: $.info.title\n    update: Outside\n");

        IOException exception = assertThrows(IOException.class,
                () -> new OverlayTargetResolver(temp, inputDirectory, List.of()).resolve(overlay));

        assertTrue(exception.getMessage().contains("inside inputDirectory"));
    }

    @Test
    void configuredGlobMatchesYamlDocuments() throws Exception {
        Path api = write("spec/api.yaml", "openapi: 3.0.3\ninfo: {}\n");
        write("spec/notes.txt", "not an API");
        Path overlay = write("overlay.yaml", "overlay: 1.1.0\ninfo:\n  title: Overlay\n  version: 1\nactions:\n  - target: $.info.title\n    update: API\n");

        List<Path> result = new OverlayTargetResolver(temp, temp, List.of("spec/**/*.yaml")).resolve(overlay);

        assertEquals(List.of(api), result);
    }

    @Test
    void defaultPatternsMatchYamlAndYmlBelowInputDirectory() throws Exception {
        Path yaml = write("documents/api.yaml", "openapi: 3.0.3\ninfo: {}\n");
        Path yml = write("documents/other.yml", "openapi: 3.0.3\ninfo: {}\n");
        Path json = write("documents/other.json", "{\"openapi\":\"3.0.3\",\"info\":{}}\n");
        Path overlay = write("overlay.yaml", "overlay: 1.1.0\ninfo:\n  title: Overlay\n  version: 1\nactions:\n  - target: $.info.title\n    update: API\n");

        List<Path> result = new OverlayTargetResolver(temp, temp.resolve("documents"), List.of()).resolve(overlay);

        assertEquals(List.of(yaml, json, yml), result);
    }

    @Test
    void configuredDoubleStarMatchesFilesAtInputRootAndBelowIt() throws Exception {
        Path input = Files.createDirectory(temp.resolve("input"));
        Path rootYaml = write("input/api.yaml", "openapi: 3.0.3\ninfo: {}\n");
        Path nestedYaml = write("input/documents/api.yaml", "openapi: 3.0.3\ninfo: {}\n");
        Path overlay = write("overlay.yaml", "overlay: 1.1.0\ninfo:\n  title: Overlay\n  version: 1\nactions:\n  - target: $.info.title\n    update: API\n");

        List<Path> result = new OverlayTargetResolver(temp, input, List.of("**/*.yaml")).resolve(overlay);

        assertEquals(List.of(rootYaml, nestedYaml), result);
    }

    private Path write(String name, String content) throws Exception {
        Path path = temp.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }
}



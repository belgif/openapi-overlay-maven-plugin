package io.github.belgif.rest.openapi.overlay.maven.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OverlayApplierTest {
    @TempDir
    Path temp;

    @Test
    void appliesOverlayUsingOverlayJvm() throws Exception {
        Path target = write("api.yaml", "openapi: 3.0.3\ninfo:\n  title: Original\n  version: 1.0.0\npaths: {}\n");
        Path overlay = write("overlay.yaml", "overlay: 1.1.0\ninfo:\n  title: Test\n  version: 1.0.0\nactions:\n  - target: $.info.title\n    update: Updated\n");

        String result = new OverlayApplier().apply(overlay, target);

        assertTrue(result.contains("Updated"), result);
    }

    @Test
    void preservesYamlTargetFormatWhenOverlayIsJson() throws Exception {
        Path target = write("api.yaml", "openapi: 3.0.3\ninfo:\n  title: Original\n  version: 1.0.0\npaths: {}\n");
        Path overlay = write("overlay.json", "{\"overlay\":\"1.1.0\",\"info\":{\"title\":\"Test\",\"version\":\"1.0.0\"},\"actions\":[{\"target\":\"$.info.title\",\"update\":\"Updated\"}]}\n");

        String result = new OverlayApplier().apply(overlay, target);

        assertTrue(result.contains("title: \"Updated\""), result);
        assertFalse(result.trim().startsWith("{"), result);
    }

    private Path write(String name, String content) throws Exception {
        Path path = temp.resolve(name);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }
}


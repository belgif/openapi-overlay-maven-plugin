package io.github.belgif.rest.openapi.overlay.maven.plugin;

import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplyOverlayMojoTest {
    @TempDir
    Path temp;

    @Test
    void discoversConventionalOverlayWritesOutputAndCanAttachResource() throws Exception {
        Path pom = Files.createFile(temp.resolve("pom.xml"));
        Path api = write("src/main/openapi/api.yaml", "openapi: 3.0.3\ninfo:\n  title: Original\n  version: 1.0.0\npaths: {}\n");
        Path untouched = write("src/main/openapi/untouched.yml", "openapi: 3.0.3\ninfo:\n  title: Untouched\n  version: 1.0.0\npaths: {}\n");
        write("src/main/openapi-overlay/title.yaml", "overlay: 1.1.0\ninfo:\n  title: Overlay\n  version: 1.0.0\nactions:\n  - target: $.info.title\n    update: Changed\n");
        Path output = temp.resolve("generated");

        MavenProject project = new MavenProject();
        project.setFile(pom.toFile());
        ApplyOverlayMojo mojo = new ApplyOverlayMojo();
        set(mojo, "project", project);
        set(mojo, "overlayDirectory", temp.resolve("src/main/openapi-overlay").toFile());
        set(mojo, "inputDirectory", temp.resolve("src/main/openapi").toFile());
        set(mojo, "inputFiles", List.of("api.yaml"));
        set(mojo, "outputDirectory", output.toFile());
        set(mojo, "addOutputToResources", true);
        mojo.setLog(new SystemStreamLog());

        mojo.execute();

        Path generated = output.resolve("api.yaml");
        Path copied = output.resolve("untouched.yml");
        assertTrue(Files.exists(generated));
        assertTrue(Files.readString(generated).contains("Changed"));
        assertTrue(Files.exists(copied));
        assertEquals(Files.readString(untouched), Files.readString(copied));
        assertEquals(1, project.getResources().size());
        assertEquals(output.toFile().getAbsolutePath(), project.getResources().get(0).getDirectory());
        assertTrue(Files.exists(api));
    }

    @Test
    void appliesYamlOverlayToJsonAndPreservesJsonOutputFormat() throws Exception {
        Path pom = Files.createFile(temp.resolve("pom.xml"));
        write("src/main/openapi/api.json", "{\n  \"openapi\": \"3.0.3\",\n  \"info\": {\"title\": \"Original\", \"version\": \"1.0.0\"},\n  \"paths\": {}\n}\n");
        write("src/main/openapi/untouched.json", "{\"openapi\":\"3.0.3\",\"info\":{\"title\":\"Untouched\",\"version\":\"1.0.0\"},\"paths\":{}}\n");
        write("src/main/openapi-overlay/title.yaml", "overlay: 1.1.0\ninfo:\n  title: Overlay\n  version: 1.0.0\nactions:\n  - target: $.info.title\n    update: Changed\n");
        Path output = temp.resolve("generated");

        MavenProject project = new MavenProject();
        project.setFile(pom.toFile());
        ApplyOverlayMojo mojo = new ApplyOverlayMojo();
        set(mojo, "project", project);
        set(mojo, "overlayDirectory", temp.resolve("src/main/openapi-overlay").toFile());
        set(mojo, "inputDirectory", temp.resolve("src/main/openapi").toFile());
        set(mojo, "inputFiles", List.of("api.json"));
        set(mojo, "outputDirectory", output.toFile());
        mojo.setLog(new SystemStreamLog());

        mojo.execute();

        String transformed = Files.readString(output.resolve("api.json"));
        assertTrue(transformed.trim().startsWith("{"), transformed);
        assertTrue(transformed.contains("\"Changed\""), transformed);
        assertEquals(Files.readString(temp.resolve("src/main/openapi/untouched.json")),
                Files.readString(output.resolve("untouched.json")));
    }

    private Path write(String name, String content) throws Exception {
        Path path = temp.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
}


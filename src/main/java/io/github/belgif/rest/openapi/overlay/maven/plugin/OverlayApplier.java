package io.github.belgif.rest.openapi.overlay.maven.plugin;

import com.webfuzzing.overlayjvm.OverlayJVM;
import com.webfuzzing.overlayjvm.TransformationResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Applies an OpenAPI overlay using the overlay-jvm implementation. */
public final class OverlayApplier {
    public String apply(Path overlay, Path target) throws IOException {
        String overlayText = Files.readString(overlay, StandardCharsets.UTF_8);
        String targetText = Files.readString(target, StandardCharsets.UTF_8);
        return apply(overlayText, targetText);
    }

    public String apply(String overlayText, String targetText) {
        TransformationResult result = OverlayJVM.applyOverlay(targetText, overlayText);
        return result.transformedSchema;
    }
}



package com.webfuzzing.overlayjvm;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonPathUtilsTest {
    private final ONode root = ONode.ofJson("{\"a\":{\"schema\":{},\"b\":{\"schema\":{}}}}");

    @Test
    void returnsMatchingExpressionUnchanged() {
        assertEquals("$.a..schema", JsonPathUtils.closestMatch(root, "$.a..schema"));
        assertEquals("$", JsonPathUtils.closestMatch(root, "$"));
    }

    @Test
    void shortensUnmatchedChildSelectors() {
        assertEquals("$.a.b", JsonPathUtils.closestMatch(root, "$.a.b.absent"));
    }

    @Test
    void skipsPrefixesEndingInDescendantMarkers() {
        assertEquals("$.a", JsonPathUtils.closestMatch(root, "$.a..absent"));
        assertEquals("$", JsonPathUtils.closestMatch(root, "$.missing..schema"));
        assertEquals("$", JsonPathUtils.closestMatch(root, "$..absent"));
        assertEquals("$.a..b", JsonPathUtils.closestMatch(root, "$.a..b..absent"));
        assertEquals("$.a", JsonPathUtils.closestMatch(root, "$.a..absent..schema"));
    }

    @Test
    void preservesInputValidation() {
        assertThrows(IllegalArgumentException.class, () -> JsonPathUtils.closestMatch(null, "$"));
        assertThrows(IllegalArgumentException.class, () -> JsonPathUtils.closestMatch(root, null));
        assertThrows(IllegalArgumentException.class, () -> JsonPathUtils.closestMatch(root, ""));
        assertThrows(IllegalArgumentException.class, () -> JsonPathUtils.closestMatch(root, "@.a"));
    }

    @Test
    void overlayJvmSkipsUnmatchedDescendantsAndUpdatesAllMatchingSchemas() {
        String overlay = """
                overlay: 1.0.0
                info:
                  title: Recursive descent regression
                  version: 1.0.0
                actions:
                  - target: $.missing..schema
                    update:
                      description: Must not be inserted
                  - target: $.a..schema
                    update:
                      description: Updated
                """;

        TransformationResult result = OverlayJVM.applyOverlay(root.toJson(), overlay);
        ONode transformed = ONode.ofJson(result.transformedSchema);

        assertEquals("Updated", transformed.get("a").get("schema").get("description").getString());
        assertEquals("Updated", transformed.get("a").get("b").get("schema").get("description").getString());
        assertFalse(transformed.hasKey("missing"));
    }
}


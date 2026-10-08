package com.webfuzzing.overlayjvm;

import org.noear.snack4.ONode;
import org.noear.snack4.jsonpath.JsonPath;
import org.noear.snack4.jsonpath.segment.DescendantSegment;

/**
 * Local override of overlay-jvm 0.3.0's JSONPath diagnostic helper.
 * Adapted from <a href="https://github.com/WebFuzzing/overlay-jvm">overlay-jvm</a>
 * (Apache License 2.0).
 * Remove this override once the upstream closest-match implementation handles
 * unmatched descendant selectors without constructing a prefix ending in {@code ..}.
 */
public class JsonPathUtils {

    /**
     * Returns the longest valid prefix selecting at least one node, or {@code $}.
     * A descendant marker and its following selector must be shortened together.
     */
    public static String closestMatch(ONode root, String jsonpath) {
        if (root == null || jsonpath == null) {
            throw new IllegalArgumentException("Null inputs");
        }
        if (jsonpath.isEmpty()) {
            throw new IllegalArgumentException("jsonpath is empty");
        }
        if (!jsonpath.startsWith("$")) {
            throw new IllegalArgumentException("jsonpath must start with '$'");
        }

        JsonPath path = JsonPath.parse(jsonpath);
        if (!path.select(root).isEmpty()) {
            return jsonpath;
        }

        for (int level = path.getSegmentCount() - 1; level > 0; level--) {
            if (path.getSegments().get(level - 1) instanceof DescendantSegment) {
                continue;
            }
            JsonPath prefix = path.subPath(level);
            if (!prefix.select(root).isEmpty()) {
                return prefix.getExpression();
            }
        }

        return "$";
    }
}


This Maven plugin can be used to apply overlays to OpenAPI documents during the build process, enabling developers to customize and extend their API definitions without modifying the original OpenAPI files directly.

The format of these overlay documents is specified by the [OpenAPI Overlay Specification](https://spec.openapis.org/overlay/latest.html).

The input OpenAPI document(s) can be specified, in order of descending priority, by:
* in the `extends` field of the overlay document, with a relative path to a file in the Maven project
* in the Maven plugin configuration, allowing wildcards to match multiple OpenAPI documents relative to `inputDirectory`
* if these are not configured, the overlay is applied to all `*.json`, `*.yaml`, and `*.yml` documents below `src/main/openapi/`.

The output location is by default in the `target/generated-resources/openapi` directory of the Maven project, but can be configured in the Maven plugin configuration.

This plugin uses the [WebFuzzing's overlay-jvm library](https://github.com/WebFuzzing/overlay-jvm) to apply overlays to OpenAPI documents.

## Usage

The plugin goal is `openapi-overlay:apply` and is bound by default to the
`generate-resources` phase. Overlay files are discovered recursively in
`src/main/openapi-overlay/` by convention. This directory can be changed with
`overlayDirectory`, or individual files and wildcard patterns can be supplied
with `overlays`.

The `outputDirectory` will contain all input OpenAPI documents, with overlays applied. 
OpenAPI files can use JSON or YAML independently; overlay and target formats may be mixed. 
Files that are not targeted by an overlay are copied unchanged to the output directory.
Output files preserve their paths relative to `inputDirectory` and their original file format.

Set`addOutputToResources` to `true` to add the output directory to the project's
resources (i.e. in the build artifact); it defaults to `false`.
If using the transformed OpenAPI documents as input for `openapi-generator-maven-plugin`, 
the plugin should be bound to the `generate-sources` Maven lifecycle phase, and listed before the `openapi-generator-maven-plugin` to be executed in the correct order.

For each overlay, the target resolution precedence is: its `extends` path,
then configured `inputFiles`, then all JSON and YAML files below `inputDirectory`.
An `extends` path must resolve to an existing file inside `inputDirectory`.

These are the default configuration values for the plugin:

```xml
<plugin>
  <groupId>io.github.belgif.rest.openapi</groupId>
  <artifactId>openapi-overlay-maven-plugin</artifactId>
  <version>1.0.0</version> <!-- replace by latest version -->
  <executions>
    <execution>
      <id>apply-openapi-overlays</id>
      <phase>generate-resources</phase>
      <goals>
        <goal>apply</goal>
      </goals>
      <configuration>
        <overlays/> <!-- list of overlay files. If empty (the default), all JSON and YAML files in overlayDirectory are used. -->
        <overlayDirectory>${project.basedir}/src/main/openapi-overlay</overlayDirectory>
        <inputDirectory>${project.basedir}/src/main/openapi</inputDirectory>
        <inputFiles>
          <inputFile>**/*.yaml</inputFile>
          <inputFile>**/*.yml</inputFile>
          <inputFile>**/*.json</inputFile>
        </inputFiles>
        <outputDirectory>${project.build.directory}/generated-resources/openapi</outputDirectory>
        <addOutputToResources>false</addOutputToResources>
      </configuration>
    </execution>
  </executions>
</plugin>
```

## Use cases

Example use cases for using OpenAPI overlays include:

* Adding or removing vendor extensions to an OpenAPI document only used for internal tooling, for example to add Jakarta Bean Validation annotations for the server-side implementation
* Adapt a third party OpenAPI document to your own needs, such as improving its compatibility with code generation tooling, adding a `servers` section or changing the `basePath`
* Adding common traits to definitions, such as standard headers for each operation

You can find more use cases and examples of overlays on the [Learn OpenAPI website](https://learn.openapis.org/overlay/).
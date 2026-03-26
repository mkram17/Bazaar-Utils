package com.github.mkram17.bazaarutils.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public abstract class ModuleRegistryGeneratingTask extends DefaultTask {
    @InputDirectory
    public abstract DirectoryProperty getSourcesDir();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    public void generate() throws IOException {
        Path sourcesDir = getSourcesDir().get().getAsFile().toPath();

        Map<String, String> annotationToList = discoverAutoCollectAnnotations(sourcesDir);
        getLogger().lifecycle("Discovered @AutoCollect annotations: {}", annotationToList);

        Map<String, List<Entry>> grouped = collectAnnotated(sourcesDir, annotationToList);
        grouped.forEach((name, entries) -> getLogger().lifecycle("BazaarUtils{} → {} entries: {}", name, entries.size(), entries));

        writeRegistry(grouped);
    }

    private Map<String, String> discoverAutoCollectAnnotations(Path sourcesDir) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();

        try (var paths = Files.walk(sourcesDir)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> scanForAutoCollect(p, result));
        }

        return result;
    }

    private void scanForAutoCollect(Path path, Map<String, String> result) {
        try {
            String source = stripComments(Files.readString(path));
            if (!source.contains("@AutoCollect")) return;

            Matcher autoCollect = Pattern.compile("@AutoCollect\\(\"([^\"]+)\"\\)").matcher(source);
            if (!autoCollect.find()) return;

            Matcher annotation = Pattern.compile("@interface\\s+(\\w+)").matcher(source);
            if (!annotation.find()) return;

            result.put(annotation.group(1), autoCollect.group(1));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, List<Entry>> collectAnnotated(Path sourcesDir, Map<String, String> annotationToList) throws IOException {
        Map<String, List<Entry>> grouped = new LinkedHashMap<>();
        annotationToList.values().forEach(name -> grouped.put(name, new ArrayList<>()));

        try (var paths = Files.walk(sourcesDir)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> scanSourceFile(p, annotationToList, grouped));
        }

        return grouped;
    }

    private void scanSourceFile(Path path, Map<String, String> annotationToList, Map<String, List<Entry>> grouped) {
        try {
            String source = stripComments(Files.readString(path));
            String packagePrefix = parsePackagePrefix(source);
            Map<String, String> imports = parseImports(source);
            List<ClassDecl> decls = parseClassDeclarations(source);

            scanClasses(source, decls, packagePrefix, annotationToList, grouped);
            scanFields(source, decls, packagePrefix, imports, annotationToList, grouped);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void scanClasses(String source, List<ClassDecl> decls, String packagePrefix, Map<String, String> annotationToList, Map<String, List<Entry>> grouped) {
        for (int i = 0; i < decls.size(); i++) {
            ClassDecl decl = decls.get(i);
            String preceding = source.substring(i == 0 ? 0 : decls.get(i - 1).pos(), decl.pos());
            String fqn = buildFqn(source, decls, i, packagePrefix);

            for (Map.Entry<String, String> entry : annotationToList.entrySet()) {
                if (preceding.contains("@" + entry.getKey())) {
                    grouped.get(entry.getValue()).add(new Entry.ClassEntry(fqn));
                    break;
                }
            }
        }
    }

    private void scanFields(String source, List<ClassDecl> decls, String packagePrefix, Map<String, String> imports, Map<String, String> annotationToList, Map<String, List<Entry>> grouped) {
        Matcher fieldMatcher = Pattern.compile("public\\s+static\\s+(?:final\\s+)?(\\w[\\w.<>]*)\\s+(\\w+)\\s*=").matcher(source);

        while (fieldMatcher.find()) {
            int fieldPos = fieldMatcher.start();
            String simpleType = fieldMatcher.group(1);
            String fieldName = fieldMatcher.group(2);

            String outerFqn = findEnclosingClassFqn(source, decls, fieldPos, packagePrefix);
            if (outerFqn == null) continue;

            String preceding = precedingText(source, decls, fieldPos);

            for (Map.Entry<String, String> entry : annotationToList.entrySet()) {
                if (preceding.contains("@" + entry.getKey())) {
                    String resolvedType = resolveType(simpleType, imports);
                    grouped.get(entry.getValue()).add(new Entry.FieldEntry(outerFqn, fieldName, resolvedType));

                    break;
                }
            }
        }
    }

    private static String parsePackagePrefix(String source) {
        Matcher matcher = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE).matcher(source);

        return matcher.find() ? matcher.group(1) + "." : "";
    }

    private static Map<String, String> parseImports(String source) {
        Map<String, String> imports = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("^import\\s+([\\w.]+);", Pattern.MULTILINE).matcher(source);

        while (matcher.find()) {
            String fqn = matcher.group(1);
            String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
            imports.put(simple, fqn);
        }

        return imports;
    }

    private static List<ClassDecl> parseClassDeclarations(String source) {
        Matcher matcher = Pattern.compile("(?:public\\s+)?(?:protected\\s+)?(?:private\\s+)?" + "(?:static\\s+)?(?:final\\s+)?(?:abstract\\s+)?class\\s+(\\w+)").matcher(source);

        List<ClassDecl> decls = new ArrayList<>();

        while (matcher.find()) {
            decls.add(new ClassDecl(matcher.start(), matcher.group(1)));
        }

        return decls;
    }

    private static String precedingText(String source, List<ClassDecl> decls, int fieldPos) {
        int start = 0;

        for (ClassDecl decl : decls) {
            if (decl.pos() < fieldPos) start = decl.pos();
        }

        return source.substring(start, fieldPos);
    }

    private static String resolveType(String simpleType, Map<String, String> imports) {
        String baseName = simpleType.replaceAll("<.*>", "").trim();
        String resolved = imports.getOrDefault(baseName, baseName);
        String withBase = simpleType.replace(baseName, resolved);

        StringBuffer sb = new StringBuffer();

        Matcher matcher = Pattern.compile("<(\\w+)>").matcher(withBase);

        while (matcher.find()) {
            String inner = imports.getOrDefault(matcher.group(1), matcher.group(1));
            matcher.appendReplacement(sb, "<" + inner + ">");
        }

        matcher.appendTail(sb);

        return sb.toString();
    }

    private static String buildFqn(String source, List<ClassDecl> decls, int index, String packagePrefix) {
        ClassDecl decl = decls.get(index);
        long open  = braceCount(source, decl.pos(), '{');
        long close = braceCount(source, decl.pos(), '}');

        if (open <= close) return packagePrefix + decl.name(); // top-level

        for (int j = index - 1; j >= 0; j--) {
            long o = braceCount(source, decls.get(j).pos(), '{');
            long c = braceCount(source, decls.get(j).pos(), '}');
            if (o <= close) return packagePrefix + decls.get(j).name() + "." + decl.name();
        }
        return packagePrefix + decl.name();
    }

    private static String findEnclosingClassFqn(String source, List<ClassDecl> decls, int fieldPos, String packagePrefix) {
        long fieldDepth = braceCount(source, fieldPos, '{') - braceCount(source, fieldPos, '}');

        for (int i = decls.size() - 1; i >= 0; i--) {
            ClassDecl decl = decls.get(i);
            if (decl.pos() >= fieldPos) continue;
            long depth = braceCount(source, decl.pos(), '{') - braceCount(source, decl.pos(), '}');
            if (depth < fieldDepth) return buildFqn(source, decls, i, packagePrefix);
        }
        return null;
    }

    private static long braceCount(String source, int upTo, char brace) {
        return source.substring(0, upTo).chars().filter(c -> c == brace).count();
    }

    private record ClassDecl(int pos, String name) {}

    private sealed interface Entry {
        record ClassEntry(String fqn) implements Entry {}
        record FieldEntry(String outerFqn, String fieldName, String declaredType) implements Entry {}

        default String toFieldName() {
            return switch (this) {
                case ClassEntry e -> Arrays.stream(e.fqn().split("\\."))
                        .filter(s -> Character.isUpperCase(s.charAt(0)))
                        .collect(Collectors.joining("_"));
                case FieldEntry e -> Arrays.stream(e.outerFqn().split("\\."))
                        .filter(s -> Character.isUpperCase(s.charAt(0)))
                        .collect(Collectors.joining("_")) + "_" + e.fieldName();
            };
        }

        default String declaredType() {
            return switch (this) {
                case ClassEntry e -> e.fqn();
                case FieldEntry e -> e.declaredType();
            };
        }

        default String initializer() {
            return switch (this) {
                case ClassEntry e -> "new " + e.fqn() + "()";
                case FieldEntry e -> e.outerFqn() + "." + e.fieldName();
            };
        }
    }

    private void writeRegistry(Map<String, List<Entry>> grouped) throws IOException {
        String pkg = "com.github.mkram17.bazaarutils.generated";
        Path outDir = getOutputDir().get().getAsFile().toPath().resolve(pkg.replace('.', '/'));
        Files.createDirectories(outDir);

        for (Map.Entry<String, List<Entry>> entry : grouped.entrySet()) {
            String className = "BazaarUtils" + entry.getKey();
            Files.writeString(outDir.resolve(className + ".java"), buildRegistrySource(pkg, className, entry.getValue()));
            getLogger().lifecycle("Generated {}.java with {} entries", className, entry.getValue().size());
        }
    }

    private static String buildRegistrySource(String pkg, String className, List<Entry> entries) {
        var sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append("import java.util.List;\n");
        sb.append("import java.util.ArrayList;\n");
        sb.append("import java.util.function.Consumer;\n\n");
        sb.append("// Generated by ModuleRegistryGeneratingTask — do not edit\n");
        sb.append("public final class ").append(className).append(" {\n\n");

        for (Entry entry : entries) {
            sb.append("    public static ").append(entry.declaredType()).append(" ").append(entry.toFieldName()).append(";\n");
        }

        if (!entries.isEmpty()) sb.append("\n");

        sb.append("    public static final List<Object> collected = new ArrayList<>();\n\n");

        sb.append("    public static void init() {\n");
        for (Entry entry : entries) {
            String field = entry.toFieldName();
            sb.append("        ").append(field).append(" = ").append(entry.initializer()).append(";\n");
            sb.append("        collected.add(").append(field).append(");\n");
        }
        sb.append("    }\n\n");

        sb.append("    public static void init(Consumer<Object> applicator) {\n");
        for (Entry entry : entries) {
            String field = entry.toFieldName();
            sb.append("        ").append(field).append(" = ").append(entry.initializer()).append(";\n");
            sb.append("        collected.add(").append(field).append(");\n");
            sb.append("        applicator.accept(").append(field).append(");\n");
        }
        sb.append("    }\n\n");

        sb.append("    public static void forEach(Consumer<Object> applicator) {\n");
        sb.append("        collected.forEach(applicator);\n");
        sb.append("    }\n");

        sb.append("}\n");

        return sb.toString();
    }

    private static String stripComments(String source) {
        StringBuilder sb = new StringBuilder();
        int i = 0, len = source.length();

        while (i < len) {
            char c = source.charAt(i);

            if (c == '"' || c == '\'') {
                // String or char literal — copy verbatim, respecting escapes
                sb.append(c);
                i++;
                while (i < len) {
                    char sc = source.charAt(i);
                    sb.append(sc);
                    if (sc == '\\') {
                        i++;
                        if (i < len) { sb.append(source.charAt(i)); i++; }
                    } else if (sc == c) { // matching closing quote
                        i++;
                        break;
                    } else {
                        i++;
                    }
                }
            } else if (c == '/' && i + 1 < len) {
                char next = source.charAt(i + 1);

                if (next == '/') {
                    // Single-line comment: skip through to end of line
                    while (i < len && source.charAt(i) != '\n') i++;
                } else if (next == '*') {
                    // Block/Javadoc comment: skip through to closing */
                    i += 2;
                    while (i + 1 < len && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) i++;
                    i += 2; // consume the closing */
                } else {
                    sb.append(c);
                    i++;
                }
            } else {
                sb.append(c);
                i++;
            }
        }

        return sb.toString();
    }
}
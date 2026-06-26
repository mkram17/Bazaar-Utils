package com.github.mkram17.bazaarutils.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public abstract class BuildtimeInjectionTask extends DefaultTask {

    @org.gradle.api.tasks.Internal
    public abstract DirectoryProperty getClassesDir();

    @TaskAction
    public void execute() {
        getLogger().info("Starting build-time bytecode injection.");
        long start = System.currentTimeMillis();

        if (!getClassesDir().isPresent()) {
            getLogger().info("Classes directory not set.");
            return;
        }
        File classesDir = getClassesDir().get().getAsFile();
        if (!classesDir.exists()) {
            getLogger().info("Classes directory does not exist: {}", classesDir);
            return;
        }
        Map<MethodReference, Integer> initMethods = new HashMap<>();
        List<MethodReference> widgetMethods = new ArrayList<>();

        // 1. Find all annotated methods
        findAnnotatedMethods(classesDir, initMethods, widgetMethods);

        // 2. Process @RunOnInit methods
        processInitMethods(classesDir, initMethods);

        // 3. Process @RegisterWidget methods
        processWidgetMethods(classesDir, widgetMethods);

        getLogger().lifecycle("Build-time injection took: {}ms", (System.currentTimeMillis() - start));
    }

    private void findAnnotatedMethods(File classesDir, Map<MethodReference, Integer> initMethods, List<MethodReference> widgetMethods) {
        forEachClass(classesDir, bytes -> {
            try {
                bytes[7] = 65; // downgrade to Java 21 for ASM
                ClassReader classReader = new ClassReader(bytes);
                classReader.accept(new AnnotationReadingClassVisitor(classReader, initMethods, widgetMethods), ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            } catch (Exception e) {
                throw new RuntimeException("Failed to read class for annotation processing", e);
            }
        });
    }

    private void processInitMethods(File classesDir, Map<MethodReference, Integer> initMethods) {
        if (initMethods.isEmpty()) {
            getLogger().info("No @RunOnInit methods found.");
            return;
        }

        List<MethodReference> sortedInitMethods = initMethods.entrySet()
                .stream()
                .sorted(Map.Entry.<MethodReference, Integer>comparingByValue().thenComparing(entry -> entry.getKey().className()))
                .map(Map.Entry::getKey)
                .toList();

        getLogger().info("Found {} @RunOnInit methods to inject.", sortedInitMethods.size());
        injectInitCalls(classesDir, sortedInitMethods);
    }

    private void processWidgetMethods(File classesDir, List<MethodReference> widgetMethods) {
        if (widgetMethods.isEmpty()) {
            getLogger().info("No @RegisterWidget methods found.");
            return;
        }
        getLogger().info("Found {} @RegisterWidget methods to inject.", widgetMethods.size());
        injectWidgetCalls(classesDir, widgetMethods);
    }

    private void injectInitCalls(File classesDir, List<MethodReference> methodSignatures) {
        String mainClassName = "com/github/mkram17/bazaarutils/BazaarUtils.class";
        Path mainClassFile = Objects.requireNonNull(findClass(classesDir, mainClassName), "BazaarUtils class wasn't found").toPath();

        try {
            byte[] bytes = Files.readAllBytes(mainClassFile);
            bytes[7] = 65;
            ClassReader classReader = new ClassReader(bytes);
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            classReader.accept(new InitInjectingClassVisitor(classWriter, methodSignatures, "onInitializeClient", "()V"), 0);
            try (OutputStream outputStream = Files.newOutputStream(mainClassFile)) {
                outputStream.write(classWriter.toByteArray());
            }
        } catch (Exception e) {
            getLogger().error("Failed to inject init calls into {}", mainClassFile, e);
        }
    }

    private void injectWidgetCalls(File classesDir, List<MethodReference> methodSignatures) {
        String configClassName = "com/github/mkram17/bazaarutils/config/BUConfig.class";
        Path configClassFile = Objects.requireNonNull(findClass(classesDir, configClassName), "BUConfig class wasn't found").toPath();

        try {
            byte[] bytes = Files.readAllBytes(configClassFile);
            bytes[7] = 65;
            ClassReader classReader = new ClassReader(bytes);
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            classReader.accept(new WidgetInjectingClassVisitor(classWriter, methodSignatures, "getWidgets", "()Ljava/util/List;"), 0);
            try (OutputStream outputStream = Files.newOutputStream(configClassFile)) {
                outputStream.write(classWriter.toByteArray());
            }
        } catch (Exception e) {
            getLogger().error("Failed to inject widget calls into {}", configClassFile, e);
        }
    }

    private void forEachClass(File directory, final Consumer<byte[]> consumer) {
        try {
            Files.walkFileTree(directory.toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                    if (!path.toString().endsWith(".class")) return FileVisitResult.CONTINUE;
                    try {
                        consumer.accept(Files.readAllBytes(path));
                    } catch (IOException e) {
                        getLogger().error("Failed to run consumer on class {}", path, e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            getLogger().error("Failed to walk class directory {}", directory, e);
        }
    }

    private File findClass(File directory, String className) {
        if (!className.endsWith(".class")) className += ".class";
        Path classPath = directory.toPath().resolve(className);
        return Files.exists(classPath) ? classPath.toFile() : null;
    }

    public record MethodReference(String className, String methodName, String descriptor) {}
}
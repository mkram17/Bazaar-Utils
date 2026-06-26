package com.github.mkram17.bazaarutils.build;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.*;

import java.util.List;
import java.util.Map;

public class AnnotationReadingClassVisitor extends ClassVisitor {
    private final Map<BuildtimeInjectionTask.MethodReference, Integer> initMethods;
    private final List<BuildtimeInjectionTask.MethodReference> widgetMethods;
    private final ClassReader classReader;

    public AnnotationReadingClassVisitor(ClassReader classReader, Map<BuildtimeInjectionTask.MethodReference, Integer> initMethods, List<BuildtimeInjectionTask.MethodReference> widgetMethods) {
        super(Opcodes.ASM9, null); // We don't pass a visitor to the super constructor, we handle it manually.
        this.classReader = classReader;
        this.initMethods = initMethods;
        this.widgetMethods = widgetMethods;
    }

    @Override
    public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature, String[] exceptions) {
        // We create a custom MethodVisitor to intercept annotations.
        return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, methodName, descriptor, signature, exceptions)) {
            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                if (desc.equals("Lcom/github/mkram17/bazaarutils/misc/autoregistration/RunOnInit;")) {
                    return new InitAnnotationVisitor(initMethods, createMethodReference(methodName, descriptor, access));
                }
                if (desc.equals("Lcom/github/mkram17/bazaarutils/misc/autoregistration/RegisterWidget;")) {
                    validateAndAddWidgetMethod(methodName, descriptor, access);
                    // This annotation has no values, so we don't need to visit it.
                    // We still pass the annotation to the next visitor in the chain.
                    return super.visitAnnotation(desc, visible);
                }
                return super.visitAnnotation(desc, visible);
            }
        };
    }

    private void validateAndAddWidgetMethod(String methodName, String descriptor, int access) {
        String className = classReader.getClassName();
        String methodCallString = className + "." + methodName;
        if ((access & Opcodes.ACC_PUBLIC) == 0) throw new IllegalStateException(methodCallString + ": @RegisterWidget method must be public");
        if ((access & Opcodes.ACC_STATIC) == 0) throw new IllegalStateException(methodCallString + ": @RegisterWidget method must be static");

        // Check for valid return types
        if (!descriptor.equals("()Ljava/util/List;") && !descriptor.equals("()Ljava/util/Collection;")) {
            throw new IllegalStateException(methodCallString + ": @RegisterWidget method must return java.util.List or java.util.Collection");
        }

        widgetMethods.add(new BuildtimeInjectionTask.MethodReference(className, methodName, descriptor));
    }

    private @NotNull BuildtimeInjectionTask.MethodReference createMethodReference(String methodName, String descriptor, int access) {
        String className = classReader.getClassName();
        String methodCallString = className + "." + methodName;
        if ((access & Opcodes.ACC_PUBLIC) == 0) throw new IllegalStateException(methodCallString + ": @RunOnInit method must be public");
        if ((access & Opcodes.ACC_STATIC) == 0) throw new IllegalStateException(methodCallString + ": @RunOnInit method must be static");
        if (!descriptor.equals("()V")) throw new IllegalStateException(methodCallString + ": @RunOnInit method must have no args and a void return type");

        return new BuildtimeInjectionTask.MethodReference(className, methodName, descriptor);
    }

    static class InitAnnotationVisitor extends AnnotationVisitor {
        private final Map<BuildtimeInjectionTask.MethodReference, Integer> methodSignatures;
        private final BuildtimeInjectionTask.MethodReference methodCall;

        protected InitAnnotationVisitor(Map<BuildtimeInjectionTask.MethodReference, Integer> methodSignatures, BuildtimeInjectionTask.MethodReference methodCall) {
            super(Opcodes.ASM9); // We don't need to chain this visitor, as we are only reading values.
            this.methodSignatures = methodSignatures;
            this.methodCall = methodCall;
        }

        @Override
        public void visitEnd() {
            methodSignatures.putIfAbsent(methodCall, 2); // Default to NORMAL priority
            super.visitEnd();
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            if (name.equals("priority")) {
                int priorityValue = switch (value) {
                    case "LOWEST" -> 0;
                    case "LOW" -> 1;
                    case "NORMAL" -> 2;
                    case "HIGH" -> 3;
                    case "HIGHEST" -> 4;
                    default -> 2;
                };
                methodSignatures.put(methodCall, priorityValue);
            }
            super.visitEnum(name, descriptor, value);
        }
    }
}
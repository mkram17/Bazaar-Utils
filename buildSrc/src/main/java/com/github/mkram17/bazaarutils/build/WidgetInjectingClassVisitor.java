package com.github.mkram17.bazaarutils.build;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WidgetInjectingClassVisitor extends ClassVisitor {
    private final List<BuildtimeInjectionTask.MethodReference> widgetMethods;
    private final String targetMethodName;
    private final String targetMethodDesc;

    public WidgetInjectingClassVisitor(ClassVisitor classVisitor, List<BuildtimeInjectionTask.MethodReference> widgetMethods, String targetMethodName, String targetMethodDesc) {
        super(Opcodes.ASM9, classVisitor);
        this.widgetMethods = widgetMethods;
        this.targetMethodName = targetMethodName;
        this.targetMethodDesc = targetMethodDesc;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

        if (mv != null && name.equals(targetMethodName) && descriptor.equals(targetMethodDesc)) {
            // This is the target method, wrap it to inject our calls before it returns.
            return new MethodVisitor(Opcodes.ASM9, mv) {
                // The task rewrites its own input directory in place, so it can be handed a class
                // it has already injected (e.g. when compileJava is restored from the build cache).
                // Track the static calls already present so a second pass is a no-op.
                private final Set<String> existingCalls = new HashSet<>();

                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                    if (opcode == Opcodes.INVOKESTATIC) {
                        existingCalls.add(owner + name + descriptor);
                    }
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }

                @Override
                public void visitInsn(int opcode) {
                    // We inject our code right before the method returns the list.
                    // The opcode for returning an object reference is ARETURN.
                    if (opcode == Opcodes.ARETURN) {
                        // The local variable storing the list (named 'widgets') is at index 0.
                        for (BuildtimeInjectionTask.MethodReference widgetMethod : widgetMethods) {
                            String uniqueKey = widgetMethod.className() + widgetMethod.methodName() + widgetMethod.descriptor();
                            if (existingCalls.contains(uniqueKey)) continue;

                            super.visitVarInsn(Opcodes.ALOAD, 0); // Load the 'widgets' ArrayList local variable.
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, widgetMethod.className(), widgetMethod.methodName(), widgetMethod.descriptor(), false);
                            super.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "addAll", "(Ljava/util/Collection;)Z", true);
                            super.visitInsn(Opcodes.POP); // Pop the boolean return value of addAll, which we don't need.
                            existingCalls.add(uniqueKey);
                        }
                    }
                    super.visitInsn(opcode);
                }
            };
        }
        return mv;
    }
}

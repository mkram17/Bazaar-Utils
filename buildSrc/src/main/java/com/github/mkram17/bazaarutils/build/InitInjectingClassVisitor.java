package com.github.mkram17.bazaarutils.build;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InitInjectingClassVisitor extends ClassVisitor {
    private final List<BuildtimeInjectionTask.MethodReference> methodSignatures;
    private final String targetMethodName;
    private final String targetMethodDesc;

    public InitInjectingClassVisitor(ClassVisitor classVisitor, List<BuildtimeInjectionTask.MethodReference> methodSignatures, String targetMethodName, String targetMethodDesc) {
        super(Opcodes.ASM9, classVisitor);
        this.methodSignatures = methodSignatures;
        this.targetMethodName = targetMethodName;
        this.targetMethodDesc = targetMethodDesc;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor originalMethodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);

        if (name.equals(targetMethodName) && descriptor.equals(targetMethodDesc)) {
            return new MethodVisitor(Opcodes.ASM9, originalMethodVisitor) {
                // Track existing calls to prevent duplicates
                private final Set<String> existingCalls = new HashSet<>();

                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                    // Record every static call we encounter in the method
                    if (opcode == Opcodes.INVOKESTATIC) {
                        existingCalls.add(owner + name + descriptor);
                    }
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }

                @Override
                public void visitInsn(int opcode) {
                    // Inject calls just before the method returns
                    if (opcode == Opcodes.RETURN) {
                        for (BuildtimeInjectionTask.MethodReference methodCall : methodSignatures) {
                            // Create a unique key for the method we want to inject
                            String uniqueKey = methodCall.className() + methodCall.methodName() + methodCall.descriptor();

                            // Only inject if it's not already in the method body
                            if (!existingCalls.contains(uniqueKey)) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, methodCall.className(), methodCall.methodName(), methodCall.descriptor(), false);
                                // Add to set to prevent double injection within the same loop if list has duplicates
                                existingCalls.add(uniqueKey);
                            }
                        }
                    }
                    super.visitInsn(opcode);
                }
            };
        }

        return originalMethodVisitor;
    }
}

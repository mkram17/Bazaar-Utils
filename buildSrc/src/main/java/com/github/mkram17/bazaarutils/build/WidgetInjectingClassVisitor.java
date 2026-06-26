package com.github.mkram17.bazaarutils.build;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;

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
                @Override
                public void visitInsn(int opcode) {
                    // We inject our code right before the method returns the list.
                    // The opcode for returning an object reference is ARETURN.
                    if (opcode == Opcodes.ARETURN) {
                        // The local variable storing the list (named 'widgets') is at index 0.
                        for (BuildtimeInjectionTask.MethodReference widgetMethod : widgetMethods) {
                            super.visitVarInsn(Opcodes.ALOAD, 0); // Load the 'widgets' ArrayList local variable.
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, widgetMethod.className(), widgetMethod.methodName(), widgetMethod.descriptor(), false);
                            super.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "addAll", "(Ljava/util/Collection;)Z", true);
                            super.visitInsn(Opcodes.POP); // Pop the boolean return value of addAll, which we don't need.
                        }
                    }
                    super.visitInsn(opcode);
                }
            };
        }
        return mv;
    }
}
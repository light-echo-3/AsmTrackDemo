package com.wuzhu.asmtrack;


import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;

/**
 * @author Hdq on 2022/12/6.
 */
public class ScanClassVisitor extends ClassVisitor {
    private boolean isInterface;
    private String className;


    public ScanClassVisitor(int api, ClassVisitor cv) {
        super(api, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
        className = name.substring(name.lastIndexOf(File.separator) + 1);//name = com/example/testandroid51/MainActivity
    }

    @Override
    public MethodVisitor visitMethod(int access,
                                     String name,
                                     String descriptor,
                                     String signature,
                                     String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (!isInterface && mv != null && !"<init>".equals(name) && !"<clinit>".equals(name)) {
            boolean isAbstractMethod = (access & Opcodes.ACC_ABSTRACT) != 0;
            boolean isNativeMethod = (access & Opcodes.ACC_NATIVE) != 0;
            if (!isAbstractMethod && !isNativeMethod) {
                mv = new MethodEnterAndExitAdapter(api, mv, className, name);
            }
        }
        return mv;

    }


}
package com.wuzhu.asmtrack;


import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.utils.FileUtils;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author Hdq on 2022/12/6.
 */
public class ASMTransform extends Transform {

    // transfrom名称
    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    // 输入源，class文件
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    // 文件范围，整个工程
    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    // 是否增量编译，可用于编译优化
    @Override
    public boolean isIncremental() {
        return false;
    }

    // 核心方法
    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation);
        System.out.println("-----transform");
        if (!transformInvocation.isIncremental()) {
            //不是增量编译删除所有的outputProvider
            transformInvocation.getOutputProvider().deleteAll();
        }
        // 获取输入源
        Collection<TransformInput> inputs = transformInvocation.getInputs();
        inputs.forEach(transformInput -> {
            Collection<DirectoryInput> directoryInputs = transformInput.getDirectoryInputs();
            directoryInputs.forEach(directoryInput -> {
                try {
                    // 处理输入源
                    handleDirectoryInput(directoryInput);
                } catch (IOException e) {
                    System.out.println("handleDirectoryInput error:" + e);
                }
            });

            for (DirectoryInput directoryInput : directoryInputs) {
                // 获取output目录
                File dest = transformInvocation.getOutputProvider().getContentLocation(
                        directoryInput.getName(),
                        directoryInput.getContentTypes(),
                        directoryInput.getScopes(),
                        Format.DIRECTORY);
                //这里执行字节码的注入，不操作字节码的话也要将输入路径拷贝到输出路径
                try {
                    FileUtils.copyDirectory(directoryInput.getFile(), dest);
                } catch (IOException e) {
                    System.out.println("output copy error:" + e);
                }
            }

            // scan all jars
            Collection<JarInput> jarInputs = transformInput.getJarInputs();
            for (JarInput jarInput : jarInputs) {
                // 获取output目录
                File dest = transformInvocation.getOutputProvider().getContentLocation(
                        jarInput.getName(),
                        jarInput.getContentTypes(),
                        jarInput.getScopes(),
                        Format.JAR);
                //这里执行字节码的注入，不操作字节码的话也要将输入路径拷贝到输出路径
                try {
                    FileUtils.copyFile(jarInput.getFile(), dest);
                } catch (IOException e) {
                    System.out.println("output copy error:" + e);
                }
            }
        });
    }

    /**
     * 处理文件目录下的class文件
     */
    private void handleDirectoryInput(DirectoryInput directoryInput) throws IOException {
        System.out.println("------" + directoryInput.getFile());
        List<File> files = new ArrayList<>();
        //列出目录所有文件（包含子文件夹，子文件夹内文件）
        listFiles(files, directoryInput.getFile());
        for (File file : files) {
            scanClass(file);
            // 方法节流
//            methodThrottleASM(file);

            // 方法耗时
//            methodTimeConsumeASM(file);
        }
    }

    private void scanClass(File inFile) {
        try {
            InputStream inputStream = new FileInputStream(inFile);
            ClassReader classReader = new ClassReader(inputStream);
            if (isNotTrack(classReader)) {
                return;
            }
            ClassWriter classWriter = new ClassWriter(classReader, 0);
            ScanClassVisitor classVisitor = new ScanClassVisitor(Opcodes.ASM5, classWriter);
            classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);

            //覆盖原来的class文件
            byte[] code = classWriter.toByteArray();
            FileOutputStream fos = new FileOutputStream(inFile.getParentFile()
                    .getAbsolutePath() + File.separator + inFile.getName());
            fos.write(code);

            fos.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 用注解 com.wuzhu.libasmtrack.NotTrack 注释的类，不插桩
     * @param reader
     * @return
     */
    private boolean isNotTrack(ClassReader reader) {
        ClassNode classNode = new ClassNode();//创建ClassNode,读取的信息会封装到这个类里面
        reader.accept(classNode, 0);//开始读取
        List<AnnotationNode> annotations = classNode.invisibleAnnotations;//获取声明的所有注解
        if (annotations != null) {//遍历注解
            for (AnnotationNode annotationNode : annotations) {
                //获取注解的描述信息
                if ("Lcom/wuzhu/libasmtrack/NotTrack;".equals(annotationNode.desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void listFiles(List<File> files, File file) {
        if (file.isFile()) {
            System.out.println("------file = " + file);
            files.add(file);
            return;
        }
        File[] subFiles = file.listFiles();
        if (subFiles != null) {
            for (File subFile : subFiles) {
                listFiles(files, subFile);
            }
        }
    }


}

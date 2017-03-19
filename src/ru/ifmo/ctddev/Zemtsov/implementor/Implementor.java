package ru.ifmo.ctddev.Zemtsov.implementor;

import info.kgeorgiy.java.advanced.implementor.Impler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * The Implementor class is used to create class
 * implementing given interface or extending given class
 *
 * @author Zemtsov Vladislav
 * @version 1.0
 * @see info.kgeorgiy.java.advanced.implementor.JarImpler
 */
public class Implementor implements JarImpler {

    /**
     * Creates java class and depend on starting arguments
     * puts it in jar file
     * <br>
     * Usage:
     * <br>
     * to create java class
     * <br>
     * java Implementor &lt;classname&gt;
     * <br>
     * to create jar file
     * <br>
     * java -jar &lt;classname&gt; &lt;filename&gt;
     * <br>
     * @param args program arguments
     */
    public static void main(String[] args) {
        if (args == null || (args.length != 1 && args.length != 3) || (args.length == 1 && args[0] != null)
                || (args.length == 3 && args[0] != null && args[1] != null && args[2] != null)) {
            System.err.println("Usage: java Implementor <classname>\n        java -jar <classname> <filename>");
        }
        boolean jarFlag = args[0].equals("-jar");

        try {
            String className;
            if (jarFlag) {
                className = args[1];
            } else {
                className = args[0];
            }
            Class c = Class.forName(className);
            if (jarFlag) {
                (new Implementor()).implementJar(c, Paths.get(args[2]));
            } else {
                (new Implementor()).implement(c, Paths.get("."));
            }
        } catch (ClassNotFoundException | ImplerException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a class implemented (or extented) from given class
     * or interface and puts it in Jar archive
     *
     * @param aClass given class
     * @param path   path to jar file where result class should be put
     * @throws ImplerException if  IOException happened {@link #getFilePath(Class, Path) {@link #implementJar(Class, Path)}},
     *                         compilation error {@link #compileClass(Path)} or error happened while creating implemeted class {@link #implement(Class, Path)}
     */
    @Override
    public void implementJar(Class<?> aClass, Path path) throws ImplerException {
        Path temp = Paths.get(".");
        this.implement(aClass, temp);
        Path javaFilePath = getFilePath(aClass, temp).normalize();
        compileClass(javaFilePath);
        Path classFilePath = getClassFileName(javaFilePath);
        javaFilePath.toFile().deleteOnExit();
        classFilePath.toFile().deleteOnExit();

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, aClass.getName() + "Impl");
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(path), manifest);
             InputStream file = Files.newInputStream(classFilePath)) {
            jarOutputStream.putNextEntry(new ZipEntry(classFilePath.toString().replace("\\", "/")));
            int bytesRead = 0;
            byte[] buffer = new byte[2048];
            while ((bytesRead = file.read(buffer)) >= 0) {
                jarOutputStream.write(buffer, 0, bytesRead);
            }
            jarOutputStream.closeEntry();
        } catch (IOException e) {
            throw new ImplerException(e);
        }
    }

    /**
     * Returns path for compiled file
     *
     * @param pathForJavaFile pat to file with java code
     * @return path to compiled file
     */
    private Path getClassFileName(Path pathForJavaFile) {
        String strPath = pathForJavaFile.toString();
        if (strPath.endsWith(".java")) {
            strPath = strPath.substring(0, strPath.length() - 4);
            strPath = strPath + "class";
        } else {
            throw new IllegalArgumentException("It is not a path of .java file");
        }
        return Paths.get(strPath);
    }

    /**
     * Compiles given java file with system java compiler
     *
     * @param path the path to the file
     * @throws ImplerException if compiler wasn't found or compilation error happened
     */
    private void compileClass(Path path) throws ImplerException {
        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        if (javaCompiler == null) {
            throw new ImplerException("Compiler wasn't found");
        }
        int resultCode = javaCompiler.run(null, null, null, path.toString());
        if (resultCode != 0) {
            throw new ImplerException("Compiler finished with error");
        }
    }

    /**
     * Gives the path to the file in folder which corresponds to the package of the given class or interface.
     *
     * @param aClass given class or interface
     * @param path   relative path
     * @return result path to java file
     * @throws ImplerException if error happened while creating directories
     */
    private Path getFilePath(Class<?> aClass, Path path) throws ImplerException {
        if (aClass.getPackage() != null) {
            path = path.resolve(aClass.getPackage().getName().replace('.', File.separatorChar) + File.separatorChar);
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new ImplerException(e);
            }
        }
        path = path.resolve(aClass.getSimpleName() + "Impl.java");
        return path;
    }

    /**
     * Creates a class implemented (or extented) from given class
     * or interface relative to given path
     *
     * @param aClass given class or interface
     * @param path   relative path
     * @throws ImplerException tron in the following situations: superclass is final or {@link Enum},
     *                         if any IOException happend while creating java class
     */
    @Override
    public void implement(Class<?> aClass, Path path) throws ImplerException {
        if (Modifier.isFinal(aClass.getModifiers())) {
            throw new ImplerException("Superclass is final");
        }
        if (aClass.equals(Enum.class)) {
            throw new ImplerException("Superclass is Enum");
        }
        path = getFilePath(aClass, path);
        try (BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path.toFile()), "UTF-8"))) {
            writePacket(bufferedWriter, aClass);
            writeClass(bufferedWriter, aClass);
            writeConstructors(bufferedWriter, aClass);
            writeMethods(bufferedWriter, aClass);
        } catch (IOException e) {
            throw new ImplerException(e);
        }
    }

    /**
     * Writes package of new class
     *
     * @param bufferedWriter writer which we use
     * @param aClass         superclass
     * @throws IOException if any problem happened while writing
     */
    private void writePacket(BufferedWriter bufferedWriter, Class<?> aClass) throws IOException {
        if (aClass.getPackage() != null) {
            bufferedWriter.write("package " + aClass.getPackage().getName() + ";");
        }
        bufferedWriter.newLine();
    }

    /**
     * Writes class declaration
     *
     * @param bufferedWriter writer which we use
     * @param aClass         superclass
     * @throws IOException if any problem happened while writing
     */
    private void writeClass(BufferedWriter bufferedWriter, Class<?> aClass) throws IOException {
        bufferedWriter.write("/** @see ");
        bufferedWriter.write(aClass.getName());
        bufferedWriter.write("*/\n");
        bufferedWriter.newLine();
        if (aClass.isInterface()) {
            bufferedWriter.write("class " + aClass.getSimpleName() + "Impl implements " + aClass.getName() + " {\n");
        } else {
            bufferedWriter.write("class " + aClass.getSimpleName() + "Impl extends " + aClass.getName() + " {\n");
        }
        bufferedWriter.newLine();
    }

    /**
     * Writes constructors for creating class
     *
     * @param bufferedWriter writer which we use
     * @param aClass         superclass
     * @throws IOException     if any problem happened while writing
     * @throws ImplerException if superclass has not any constructor
     */
    private void writeConstructors(BufferedWriter bufferedWriter, Class<?> aClass) throws IOException, ImplerException {
        if (aClass.isInterface()) {
            return;
        }
        boolean hasConstructor = false;
        for (Constructor constructor : aClass.getDeclaredConstructors()) {
            if (Modifier.isPrivate(constructor.getModifiers())) {
                continue;
            }
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(aClass.getSimpleName()).append("Impl(");
            Class<?>[] types = constructor.getParameterTypes();
            for (int i = 0; i < types.length; i++) {
                stringBuilder.append(types[i].getTypeName()).append(' ').append("arg").append(i);
                if (i < types.length - 1) {
                    stringBuilder.append(", ");
                }
            }
            stringBuilder.append(") ");
            Class[] exceptions = constructor.getExceptionTypes();
            if (exceptions.length > 0) {
                stringBuilder.append("throws ");
            }
            for (int i = 0; i < exceptions.length; ++i) {
                stringBuilder.append(exceptions[i].getName()).append(' ');
                if (i < exceptions.length - 1) {
                    stringBuilder.append(", ");
                }
            }
            stringBuilder.append("{ super(");
            for (int i = 0; i < types.length; i++) {
                stringBuilder.append("arg").append(i);
                if (i < types.length - 1) {
                    stringBuilder.append(", ");
                }
            }
            stringBuilder.append("); }\n");
            bufferedWriter.write(stringBuilder.toString());
            bufferedWriter.newLine();
            hasConstructor = true;
        }
        if (!hasConstructor) {
            throw new ImplerException("Can not implement any constructor");
        }
    }

    /**
     * Creates method declaration written in string
     *
     * @param method which should be writen
     * @return string representation
     */
    private String getMethodString(Method method) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(getModifiers(method.getModifiers()));
        stringBuilder.append(method.getReturnType().getTypeName()).append(' ').append(method.getName()).append("(");
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length; ++i) {
            stringBuilder.append(types[i].getTypeName()).append(' ').append("arg").append(i);
            if (i < types.length - 1) {
                stringBuilder.append(", ");
            }
        }
        stringBuilder.append(") ");
        return stringBuilder.toString();
    }

    /**
     * Creates modifier representation
     *
     * @param modifier given modifier
     * @return string representation
     */
    private String getModifiers(int modifier) {
        StringBuilder stringBuilder = new StringBuilder();
        if (Modifier.isPrivate(modifier)) {
            stringBuilder.append("private ");
        } else if (Modifier.isProtected(modifier)) {
            stringBuilder.append("protected ");
        } else if (Modifier.isPublic(modifier)) {
            stringBuilder.append("public ");
        }
        if (Modifier.isStatic(modifier)) {
            stringBuilder.append("static ");
        }
        if (Modifier.isFinal(modifier)) {
            stringBuilder.append("final ");
        }
        if (Modifier.isStrict(modifier)) {
            stringBuilder.append("strictfp ");
        }

        return stringBuilder.toString();
    }

    /**
     * Returns {@link HashMap} with methods which should be in result class
     *
     * @param aClass superclass
     * @return {@link HashMap} with all methods to realize
     */
    private HashSet<Method> getMethods(Class<?> aClass) {
        HashSet<Method> result = new HashSet<Method>();
        HashSet<String> defined = new HashSet<String>();
        for (Method method : aClass.getMethods()) {
            if (Modifier.isAbstract(method.getModifiers())) {
                result.add(method);
            }
            defined.add(getMethodString(method));
        }
        Class superClass = aClass;
        while (superClass != null) {
            for (Method method : superClass.getDeclaredMethods()) {
                if (Modifier.isAbstract(method.getModifiers()) && !defined.contains(getMethodString(method))) {
                    result.add(method);
                }
                defined.add(getMethodString(method));
            }
            superClass = superClass.getSuperclass();
        }
        return result;
    }

    /**
     * Writes all methods returned by {@link #getMethods(Class)}
     *
     * @param bufferedWriter writer which we use
     * @param aClass         superclass
     * @throws IOException if any problem happened while writing
     */
    private void writeMethods(BufferedWriter bufferedWriter, Class<?> aClass) throws IOException {
        HashSet<Method> allMethods = getMethods(aClass);
        for (Method method : allMethods) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(getMethodString(method));
            Class[] exceptions = method.getExceptionTypes();
            if (exceptions.length > 0) {
                stringBuilder.append("throws ");
            }
            for (int i = 0; i < exceptions.length; ++i) {
                stringBuilder.append(exceptions[i].getName()).append(' ');
                if (i < exceptions.length - 1) {
                    stringBuilder.append(", ");
                }
            }
            stringBuilder.append("{ ");
            Class<?> returningType = method.getReturnType();
            if (returningType.equals(int.class) || returningType.equals(long.class) || returningType.equals(float.class) || returningType.equals(double.class)
                    || returningType.equals(byte.class) || returningType.equals(short.class) || returningType.equals(char.class)) {
                stringBuilder.append("return 0; }\n");
            } else if (returningType.equals(boolean.class)) {
                stringBuilder.append("return true; }\n");
            } else if (returningType.equals(void.class)) {
                stringBuilder.append("return; }\n");
            } else {
                stringBuilder.append("return null; }\n");
            }
            bufferedWriter.write(stringBuilder.toString());
            bufferedWriter.newLine();
        }
        bufferedWriter.append("}");
    }


}

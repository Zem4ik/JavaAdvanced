package ru.ifmo.ctddev.Zemtsov.walk;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Created by Vlad on 12.02.2017.
 */
public class RecursiveWalk {
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static final String ERROR_CHECKSUM = "00000000";

    private static String getChecksum(Path path) {
        int h = 0x811c9dc5;
        byte[] bytes = new byte[DEFAULT_BUFFER_SIZE];
        try (FileInputStream fileInputStream = new FileInputStream(path.toFile())) {
            int result = fileInputStream.read(bytes);
            while (result != -1) {
                for (int i = 0; i < result; ++i) {
                    h = (h * 0x01000193) ^ (bytes[i] & 0xff);
                }
                result = fileInputStream.read(bytes);
            }
            return String.format("%08x", h);
        } catch (FileNotFoundException e) {
            System.err.println("Can't find file with directory: " + path);
        } catch (IOException e) {
            System.err.println("I/O exception : " + e.getLocalizedMessage() + "\nin file with directory: " + path);
        } catch (SecurityException e) {
            System.err.println("Can't get access to file with directory: " + path);
        }
        return ERROR_CHECKSUM;
    }

    private static String getResultString(String hash, String fileName) {
        return String.format("%s %s\n", hash, fileName);
    }

    public static void main(String[] args) {
        if (args == null || args.length != 2 || args[0] == null || args[1] == null) {
            System.err.println("Wrong input format. Expected: \"java Walk <input file> <output file>\"");
            return;
        }
        String inputFileDir = args[0];
        String outputFileDir = args[1];
        try (BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFileDir), "UTF-8"))) {
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFileDir), "UTF-8"))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    try {
                        Path path = Paths.get(line);
                        if (Files.isDirectory(path)) {
                            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                                @Override
                                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                    bufferedWriter.write(getResultString(getChecksum(file), file.toString()));
                                    return FileVisitResult.CONTINUE;
                                }

                                @Override
                                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                                    bufferedWriter.write(getResultString(ERROR_CHECKSUM, file.toString()));
                                    System.err.println("Invoked for a file that could not be visited: " + exc.getMessage() + "\nfile with directory: " + path);
                                    return FileVisitResult.CONTINUE;
                                }
                            });
                        } else {
                            bufferedWriter.write(getResultString(getChecksum(path), path.toString()));
                        }
                    } catch (IOException e) {
                        System.err.println("I/O exception : " + e.getMessage() + "\nin file with directory: " + line);
                    }
                }
            } catch (FileNotFoundException e) {
                System.err.println("Input file does not exist, is a directory\nrather than a regular file, or for some other reason cannot\nbe opened for reading");
            } catch (UnsupportedEncodingException e) {
                System.err.println("The character encoding is not supported in input file");
            } catch (IOException e) {
                System.err.println("I/O exception : " + e.getMessage() + "\nin input file");
            } catch (SecurityException e) {
                System.err.println("Can't get access to input file");
            }
        } catch (FileNotFoundException e) {
            System.err.println("Output file exists but is a directory\nrather than a regular file, does not exist but cannot\nbe created, or cannot be opened for any other reason");
        } catch (IOException e) {
            System.err.println("I/O exception : " + e.getMessage() + "\nin output file");
        } catch (SecurityException e) {
            System.err.println("Can't get access to output file");
        }
    }
}

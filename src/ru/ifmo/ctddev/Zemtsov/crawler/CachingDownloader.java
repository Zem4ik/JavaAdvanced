package ru.ifmo.ctddev.Zemtsov.crawler;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Downloads document from the Web and stores them in storage directory.
 *
 * @author Georgiy Korneev (kgeorgiy@kgeorgiy.info)
 */
public class CachingDownloader implements Downloader {
    private final Path directory;

    /**
     * Creates a new downloader storing documents in temporary directory.
     *
     * @throws IOException if an error occurred.
     */
    public CachingDownloader() throws IOException {
        this(Files.createTempDirectory(CachingDownloader.class.getName()));
    }

    /**
     * Creates a new downloader storing documents in specified directory.
     *
     * @param directory storage directory.
     *
     * @throws IOException if an error occurred.
     */
    public CachingDownloader(final Path directory) throws IOException {
        this.directory = directory;
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
        if (!Files.isDirectory(directory)) {
            throw new IOException(directory + " is not a directory");
        }
    }

    /**
     * Downloads document and stores it to the storage directory. An error during
     * download may result in incomplete files in storage directory.
     *
     * @param url URL of the document to download.
     *
     * @return downloaded document.
     *
     * @throws IOException if an error occurred.
     */
    public Document download(final String url) throws IOException {
        final URI uri = URLUtils.getURI(url);
        final Path file = directory.resolve(URLEncoder.encode(uri.toString(), "UTF-8"));
        if (Files.notExists(file)) {
            System.out.println("Downloading " + url);
            try (final InputStream is = uri.toURL().openStream()) {
                Files.copy(is, file);
            }
            System.out.println("Downloaded " + uri);
        } else {
            System.out.println("Already downloaded " + url);
        }
        return () -> {
            try (final InputStream is = Files.newInputStream(file)) {
                return URLUtils.extractLinks(uri, is);
            }
        };
    }
}

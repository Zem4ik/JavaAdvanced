package ru.ifmo.ctddev.Zemtsov.crawler;

/**
 * Crawls web sites.
 *
 * @author Georgiy Korneev (kgeorgiy@kgeorgiy.info)
 */
public interface Crawler extends AutoCloseable {
    Result download(String url, int depth);

    void close();
}

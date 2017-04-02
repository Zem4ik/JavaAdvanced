package ru.ifmo.ctddev.Zemtsov.crawler;


import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Created by vlad on 02.04.17.
 */
public class WebCrawler implements Crawler {
    private ArrayList<String> downloaded;
    private ConcurrentHashMap<String, IOException> errors;
    private ConcurrentHashMap<String, Document> URLMap;
    private ConcurrentHashMap<String, Integer> downloadingPerHost;
    private final Downloader downloader;
    private final int perHost;
    private final ExecutorService downloadService;
    private final ExecutorService extractService;

    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        errors = new ConcurrentHashMap<>();
        URLMap = new ConcurrentHashMap<>();
        downloadingPerHost = new ConcurrentHashMap<>();
        downloaded = new ArrayList<>();
        downloadService = Executors.newFixedThreadPool(downloaders);
        extractService = Executors.newFixedThreadPool(extractors);
        this.perHost = perHost;
        this.downloader = downloader;
    }

    @Override
    public Result download(String url, int depth) {
        try {
            String host = URLUtils.getHost(url);
            Callable<Result> currentRun = new Runnable() {
                @Override
                public void run() {
                    if (downloadingPerHost.get(host) != null && downloadingPerHost.get(host) >= perHost) {
                        downloadService.submit(this);
                    } else {
                        if (URLMap.containsKey(url)) {
                            return;
                        }
                        downloadingPerHost.compute(host, (key, value) -> (value == null) ? 1 : value + 1);
                        try {
                            Document document = downloader.download(url);
                            synchronized (downloaded) {
                                downloaded.add(url);
                            }
                            downloadingPerHost.compute(host, (key, value) -> (value == null) ? 0 : value - 1);
                            extractService.submit(() -> {
                                try {
                                    if (depth > 1) {
                                        List<String> links = document.extractLinks();
                                        for (String url : links) {
                                            downloadURL(url, depth - 1);
                                        }
                                    }
                                } catch (IOException e) {
                                    errors.compute(url, (key, value) -> e);
                                }
                            });
                        } catch (IOException e) {
                            errors.compute(url, (key, value) -> e);
                        }
                    }
                }
            };

        } catch (MalformedURLException e) {
            //ignore error
        }
        downloadService.submit(currentRun);
        return new Result(downloaded, errors);
    }

    @Override
    public void close() {
        downloadService.shutdownNow();
        extractService.shutdownNow();
    }
}

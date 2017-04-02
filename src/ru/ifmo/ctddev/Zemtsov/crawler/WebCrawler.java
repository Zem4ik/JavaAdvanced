package ru.ifmo.ctddev.Zemtsov.crawler;


import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Created by vlad on 02.04.17.
 */
public class WebCrawler implements Crawler {
    private final Downloader downloader;
    private final int perHost;
    private final ExecutorService downloadService;
    private final ExecutorService extractService;

    private class LocalInfo {
        ConcurrentHashMap<String, Boolean> URLMap;
        ConcurrentHashMap<String, Integer> downloadingPerHost;

        LocalInfo() {
            URLMap = new ConcurrentHashMap<>();
            downloadingPerHost = new ConcurrentHashMap<>();
        }
    }

    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        downloadService = Executors.newFixedThreadPool(downloaders);
        extractService = Executors.newFixedThreadPool(extractors);
        this.perHost = perHost;
        this.downloader = downloader;
    }

    @Override
    public Result download(String url, int depth) {
        List<String> downloaded = new ArrayList<>();
        Map<String, IOException> errors = new HashMap<>();
        LocalInfo localInfo = new LocalInfo();
        BlockingQueue<Future<Result>> futures = new LinkedBlockingQueue<>();
        futures.add(downloadService.submit(createDownloadingCallable(futures, url, depth, localInfo)));
        while (!futures.isEmpty()) {
            Future<Result> future = futures.poll();
            try {
                Result result = future.get();
                if(result.getDownloaded() == null) continue;
                downloaded.addAll(result.getDownloaded());
                errors.putAll(result.getErrors());
            } catch (InterruptedException | ExecutionException e) {
                //ingoring
            }
        }
        return new Result(downloaded, errors);
    }

    private Callable<Result> createDownloadingCallable(BlockingQueue<Future<Result>> futures, String url, int depth, LocalInfo localInfo) {
        return () -> {
            List<String> downloaded = new ArrayList<>();
            Map<String, IOException> errors = new HashMap<>();
            String host = URLUtils.getHost(url);
            if (localInfo.downloadingPerHost.get(host) != null && localInfo.downloadingPerHost.get(host) >= perHost) {
                Future future = downloadService.submit(createDownloadingCallable(futures, url, depth - 1, localInfo));
                futures.put(future);
            } else {
                if (localInfo.URLMap.containsKey(url)) {
                    return new Result(null, null);
                }
                localInfo.downloadingPerHost.compute(host, (key, value) -> (value == null) ? 1 : value + 1);
                try {
                    Document document = downloader.download(url);
                    downloaded.add(url);
                    localInfo.downloadingPerHost.compute(host, (key, value) -> (value == null) ? 0 : value - 1);
                    Callable<Result> extractCallable = () -> {
                        try {
                            if (depth > 1) {
                                List<String> links = document.extractLinks();
                                for (String string : links) {
                                    Future future = downloadService.submit(createDownloadingCallable(futures, string, depth - 1, localInfo));
                                    futures.put(future);
                                }
                            }
                        } catch (IOException e) {
                            errors.compute(url, (key, value) -> e);
                        }
                        return new Result(null, null);
                    };
                    Future extractFuture = extractService.submit(extractCallable);
                    futures.put(extractFuture);
                } catch (IOException e) {
                    errors.compute(url, (key, value) -> e);
                }
            }
            return new Result(downloaded, errors);
        };
    }

    @Override
    public void close()  {
        downloadService.shutdownNow();
        extractService.shutdownNow();
    }
}

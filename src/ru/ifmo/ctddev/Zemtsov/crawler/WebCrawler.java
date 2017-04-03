package ru.ifmo.ctddev.Zemtsov.crawler;


import com.sun.org.apache.regexp.internal.RE;

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

    public static void main(String[] args) {
        if (args != null && args[0] != null) {
            int downloads = 10;
            int extractors = 10;
            int perHost = 10;
            if (args[1] != null) downloads = Integer.parseInt(args[1]);
            if (args[2] != null) extractors= Integer.parseInt(args[2]);
            if (args[3] != null) perHost = Integer.parseInt(args[3]);
            try {
                WebCrawler webCrawler = new WebCrawler(new CachingDownloader(), downloads, extractors, perHost);
                Result result = webCrawler.download(args[0], 3);
                System.out.println("downloaded sites: " + result.getDownloaded().size());
                result.getDownloaded().forEach(System.out::println);
                System.out.println("number of errors: " + result.getErrors().size());
                for (Map.Entry pair : result.getErrors().entrySet()) {
                    System.out.println(pair.getKey() + " ## " + pair.getValue());
                }
                webCrawler.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("Usage: WebCrawler url [downloads [extractors [perHost]]]");
        }
    }

    private class LocalInfo {
        ConcurrentHashMap<String, Boolean> URLMap;
        ConcurrentHashMap<String, Integer> downloadingPerHost;

        LocalInfo() {
            URLMap = new ConcurrentHashMap<String, Boolean>();
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
                if (result.getDownloaded() == null) continue;
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
            final boolean[] flag = {false};

            //flag for containing this url in map
            localInfo.URLMap.compute(url, (key, value) -> {
                if (value == null) {
                    flag[0] = true;
                }
                return true;
            });

            //if this url was already downloaded we finish our work
            if (!flag[0]) return new Result(null, null);

            //waiting for the moment, when we can continue our work
            flag[0] = false;
            while (!flag[0]) {
                localInfo.downloadingPerHost.compute(host, (key, value) -> {
                    if (value == null) {
                        flag[0] = true;
                        return 1;
                    }
                    if (value >= perHost) {
                        Future<Result> future = downloadService.submit(createDownloadingCallable(futures, url, depth - 1, localInfo));
                        try {
                            futures.put(future);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        flag[0] = false;
                        return value;
                    }
                    flag[0] = true;
                    return value + 1;
                });
                if (!flag[0]) Thread.sleep(2 * 1000);
            }

            //from this moment we now that we can work with this site
            try {
                Document document = downloader.download(url);
                downloaded.add(url);
                localInfo.downloadingPerHost.compute(host, (key, value) -> (value == null) ? 0 : value - 1);
                Callable<Result> extractCallable = () -> {
                    try {
                        if (depth > 1) {
                            List<String> links = document.extractLinks();
                            for (String string : links) {
                                Future<Result> future = downloadService.submit(createDownloadingCallable(futures, string, depth - 1, localInfo));
                                futures.put(future);
                            }
                        }
                    } catch (IOException e) {
                        errors.compute(url, (key, value) -> e);
                    }
                    return new Result(null, null);
                };
                Future<Result> extractFuture = extractService.submit(extractCallable);
                futures.put(extractFuture);
            } catch (IOException e) {
                errors.compute(url, (key, value) -> e);
            }
            return new Result(downloaded, errors);
        };
    }

    @Override
    public void close() {
        downloadService.shutdownNow();
        extractService.shutdownNow();
    }
}

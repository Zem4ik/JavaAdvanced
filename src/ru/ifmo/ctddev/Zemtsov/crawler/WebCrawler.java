package ru.ifmo.ctddev.Zemtsov.crawler;

import com.sun.org.apache.regexp.internal.RE;
import com.sun.org.apache.xpath.internal.SourceTree;
import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Created by vlad on 02.04.17.
 */
public class WebCrawler implements Crawler {
    private final Downloader downloader;
    private final int perHost;
    private final ThreadPoolExecutor downloadService;
    private final ThreadPoolExecutor extractService;

    public static void main(String[] args) {
        if (args != null && args[0] != null) {
            int downloads = 10;
            int extractors = 10;
            int perHost = 10;
            if (args[1] != null) downloads = Integer.parseInt(args[1]);
            if (args[2] != null) extractors = Integer.parseInt(args[2]);
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
        //HashMap with processed URL
        final ConcurrentHashMap<String, Boolean> URLMap;
        //HashMap with host's name and count of loading
        final ConcurrentHashMap<String, Integer> downloadingPerHost;
        //Future objects for all threads
        BlockingQueue<Future<Result>> futures;
        //Delayed tasks
        BlockingQueue<Callable<Result>> delayed;

        LocalInfo() {
            futures = new LinkedBlockingQueue<>();
            URLMap = new ConcurrentHashMap<>();
            downloadingPerHost = new ConcurrentHashMap<>();
            delayed = new LinkedBlockingQueue<>();
        }
    }

    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        //testing with fixed thread pool
//        downloadService = (ThreadPoolExecutor) Executors.newFixedThreadPool(Integer.min(downloaders, 5000));
//        extractService = (ThreadPoolExecutor) Executors.newFixedThreadPool(Integer.min(extractors, 5000));

        downloadService = (ThreadPoolExecutor) Executors.newFixedThreadPool(downloaders);
        extractService = (ThreadPoolExecutor) Executors.newFixedThreadPool(extractors);
        this.perHost = perHost;
        this.downloader = downloader;
    }

    @Override
    public Result download(String url, int depth) {
        //list of all sites
        List<String> downloaded = new ArrayList<>();
        Map<String, IOException> errors = new HashMap<>();
        LocalInfo localInfo = new LocalInfo();
        localInfo.URLMap.put(url, true);
        localInfo.futures.add(downloadService.submit(createDownloadingCallable(url, depth, localInfo)));
        while (!localInfo.futures.isEmpty()) {
            try {
                Future<Result> future = localInfo.futures.poll();
                Result result = future.get();
                if (result != null) {
                    downloaded.addAll(result.getDownloaded());
                    errors.putAll(result.getErrors());
                }
                while (!localInfo.delayed.isEmpty()) {
                    localInfo.futures.put(downloadService.submit(localInfo.delayed.poll()));
                }
            } catch (InterruptedException | ExecutionException e) {
                //debug info
                e.getCause().printStackTrace();
                System.out.println(extractService.getPoolSize() + " " + downloadService.getPoolSize() + " " + Thread.activeCount());
            }
        }

        return new Result(downloaded, errors);
    }

    private Callable<Result> createDownloadingCallable(final String url, final int depth, final LocalInfo localInfo) {
        return () -> {
            List<String> downloaded = new ArrayList<>();
            Map<String, IOException> errors = new HashMap<>();

//            if (localInfo.URLMap.put(url, true) != null) {
//                return null;
//            }

            String host;
            try {
                host = URLUtils.getHost(url);
            } catch (MalformedURLException e) {
                host = url;
            }

            //checking ow many threads are working with current host
            int newVal = localInfo.downloadingPerHost.compute(host, (key, value) -> {
                if (value == null) {
                    return 1;
                }
                return value + 1;
            });
            if (newVal > perHost) {
//              localInfo.URLMap.remove(url);
                localInfo.downloadingPerHost.compute(host, (key, value) -> value - 1);
                //localInfo.futures.put(downloadService.submit(createDownloadingCallable(url, depth, localInfo)));
                localInfo.delayed.put(createDownloadingCallable(url, depth, localInfo));
                return null;
            }

            //in this moment we can work with this site
            try {
                Document document = downloader.download(url);
                downloaded.add(url);
                localInfo.downloadingPerHost.compute(host, (key, value) -> value - 1);
                //pre: document was downloaded
                //callable for extracting, if depth > 1
                localInfo.futures.put(extractService.submit(() -> {
                    try {
                        if (depth > 1) {
                            List<String> links = document.extractLinks();
                            for (String newUrl : links) {
                                if (localInfo.URLMap.put(newUrl, true) == null) {
                                    localInfo.futures.put(downloadService
                                            .submit(createDownloadingCallable(newUrl, depth - 1, localInfo)));
                                }
                            }
                        }
                    } catch (IOException e) {
                        errors.put(url, e);
                    }
                    return null;
                }));
            } catch (IOException e) {
                localInfo.downloadingPerHost.compute(host, (key, value) -> value - 1);
                errors.put(url, e);
            }
            return new Result(downloaded, errors);
        };
    }

    @Override
    public void close() {
//        downloadService.shutdown();
//        extractService.shutdown();
//        downloadService.shutdownNow();
//        extractService.shutdownNow();
        shutdownAndAwaitTermination(downloadService);
        shutdownAndAwaitTermination(extractService);
        System.out.println(downloadService.getLargestPoolSize() + " " + extractService.getLargestPoolSize());
    }

    private void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(60, TimeUnit.SECONDS))
                    System.err.println("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }
}

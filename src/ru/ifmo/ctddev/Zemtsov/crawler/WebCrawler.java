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
    private final ExecutorService downloadService;
    private final ExecutorService extractService;

    /*public static void main(String[] args) {
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
    }*/

    private class LocalInfo {
        //HashMap with processed URL
        final ConcurrentHashMap<String, Boolean> URLMap;
        //HashMap with host's name and count of loading
        final ConcurrentHashMap<String, Integer> downloadingPerHost;
        //Future objects for all threads
        BlockingQueue<Future<Result>> futures;

        LocalInfo() {
            futures = new LinkedBlockingQueue<>();
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
        //list of all sites
        List<String> downloaded = new ArrayList<>();
        Map<String, IOException> errors = new HashMap<>();
        LocalInfo localInfo = new LocalInfo();
        localInfo.URLMap.put(url, true);
        localInfo.futures.add(downloadService.submit(createDownloadingCallable(url, depth, localInfo)));
        while (!localInfo.futures.isEmpty()) {
            Future<Result> future = localInfo.futures.poll();
            try {
                Result result = future.get();
                //all fictive object would be skipped
                downloaded.addAll(result.getDownloaded());
                errors.putAll(result.getErrors());
            } catch (InterruptedException | ExecutionException e) {
                e.getCause().printStackTrace();
                System.out.println("DEBUG: " + java.lang.Thread.activeCount());
            }
        }
        return new Result(downloaded, errors);
    }

    private Callable<Result> createDownloadingCallable(final String url, final int depth, final LocalInfo localInfo) {
        return () -> {
            List<String> downloaded = new ArrayList<>();
            Map<String, IOException> errors = new HashMap<>();
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
                localInfo.downloadingPerHost.compute(host, (key, value) -> value - 1);
                localInfo.futures.put(downloadService.submit(createDownloadingCallable(url, depth, localInfo)));
                return new Result(downloaded, errors);
            }

            //in this moment we can work with this site
            try {
                Document document = downloader.download(url);
                downloaded.add(url);
                //pre: document was downloaded
                //callable for extracting, if depth > 1
                if (depth > 1) {
                    localInfo.futures.put(extractService.submit(createExtractingCallable(document, url, depth, localInfo)));
                }
            } catch (IOException e) {
                errors.put(url, e);
            }
            localInfo.downloadingPerHost.compute(host, (key, value) -> value - 1);
            return new Result(downloaded, errors);
        };
    }

    private Callable<Result> createExtractingCallable(final Document document, final String url, final int depth, final LocalInfo localInfo) {
        return () -> {
            HashMap<String, IOException> errors = new HashMap<>();
            try {
                List<String> links = document.extractLinks();
                for (String newUrl : links) {
                    if (localInfo.URLMap.put(newUrl, true) == null) {
                        localInfo.futures.put(downloadService
                                .submit(createDownloadingCallable(newUrl, depth - 1, localInfo)));
                    }
                }
            } catch (IOException e) {
                errors.put(url, e);
            }
            return new Result(new ArrayList<>(), errors);
        };
    }

    @Override
    public void close() {
        downloadService.shutdownNow();
        extractService.shutdownNow();
    }
}

package ru.ifmo.ctddev.Zemtsov.crawler;

import info.kgeorgiy.java.advanced.crawler.*;
import javafx.util.Pair;

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
    //HashMap with host's name and count of loading
    private final ConcurrentHashMap<String, Integer> downloadingPerHost;

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
        //Future objects for all threads
        final ConcurrentLinkedQueue<Future<Result>> futures;
        //downdloading tasks with should be submitted when number of thread working with current thread would be less then perHost
        final ConcurrentLinkedQueue<Pair<Callable<Result>, String>> delayed;

        final ConcurrentLinkedQueue<Callable<Result>> nextDepthCallables;

        LocalInfo() {
            futures = new ConcurrentLinkedQueue<>();
            delayed = new ConcurrentLinkedQueue<>();
            URLMap = new ConcurrentHashMap<>();
            nextDepthCallables = new ConcurrentLinkedQueue<>();
        }
    }

    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        //testing with fixed thread pool
        downloadingPerHost = new ConcurrentHashMap<>();
        downloadService = (ThreadPoolExecutor) Executors.newFixedThreadPool(downloaders);
        extractService = (ThreadPoolExecutor) Executors.newFixedThreadPool(extractors);
        this.perHost = perHost;
        this.downloader = downloader;
    }

    @Override
    public Result download(String url, int depth) {
        //list of all sites
        List<String> downloaded = new ArrayList<>();
        //map of result errors
        Map<String, IOException> errors = new HashMap<>();
        //structures which are used by threads
        LocalInfo localInfo = new LocalInfo();
        //init first url
        localInfo.URLMap.put(url, true);
        //starting with first site
        localInfo.futures.add(downloadService.submit(createDownloadingCallable(url, depth, localInfo)));
        while (!localInfo.futures.isEmpty()) {
            //downloading sites with current depth
            while (!localInfo.futures.isEmpty()) {
                Future<Result> future = localInfo.futures.poll();
                try {
                    Result result = future.get();
                    downloaded.addAll(result.getDownloaded());
                    errors.putAll(result.getErrors());
                } catch (InterruptedException | ExecutionException e) {
                    //debug info
                    e.getCause().printStackTrace();
                }
                while (!localInfo.delayed.isEmpty()) {
                    Pair<Callable<Result>, String> pair = localInfo.delayed.poll();
                    if (downloadingPerHost.get(pair.getValue()) >= perHost) {
                        localInfo.delayed.add(pair);
                    } else {
                        localInfo.futures.add(downloadService.submit(pair.getKey()));
                    }
                }
            }
            //putting tasks for downloading sites with lower depth
            while (!localInfo.nextDepthCallables.isEmpty()) {
                localInfo.futures.add(downloadService.submit(localInfo.nextDepthCallables.poll()));
            }
        }
        return new Result(downloaded, errors);
    }

    private Callable<Result> createDownloadingCallable(final String url, final int depth, final LocalInfo localInfo) {
        return () -> {
            List<String> downloaded = new ArrayList<>();
            Map<String, IOException> errors = new HashMap<>();

            //getting host name, if errors occurs we set host = url
            String host;
            try {
                host = URLUtils.getHost(url);
            } catch (MalformedURLException e) {
                host = url;
            }

            //checking ow many threads are working with current host
            int newVal = downloadingPerHost.compute(host, (key, value) -> {
                if (value == null) {
                    return 1;
                }
                return value + 1;
            });
            if (newVal > perHost) {
                downloadingPerHost.compute(host, (key, value) -> value - 1);
                localInfo.delayed.add(new Pair<>(createDownloadingCallable(url, depth, localInfo), host));
                return new Result(downloaded, errors);
            }

            //in this moment we can work with this site
            try {
                //downloading site
                Document document = downloader.download(url);
                downloaded.add(url);
                //callable for extracting, if depth > 1
                if (depth > 1) {
                    localInfo.futures.add(extractService.submit(createExtractingCallable(document, url, depth, localInfo)));
                }
            } catch (IOException e) {
                errors.put(url, e);
            }
            //decreasing counter of current working with this host threads
            downloadingPerHost.compute(host, (key, value) -> value - 1);
            return new Result(downloaded, errors);
        };
    }

    private Callable<Result> createExtractingCallable(final Document document, final String url, final int depth, final LocalInfo localInfo) {
        return () -> {
            HashMap<String, IOException> errors = new HashMap<>();
            try {
                //extracting links
                List<String> links = document.extractLinks();
                //putting callable for downloading sites with lower depth in queue
                for (String newUrl : links) {
                    if (localInfo.URLMap.put(newUrl, true) == null) {
                        localInfo.nextDepthCallables.add(createDownloadingCallable(newUrl, depth - 1, localInfo));
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
        System.out.println(downloadService.getLargestPoolSize() + " " + extractService.getLargestPoolSize() + " " + perHost);
    }
}

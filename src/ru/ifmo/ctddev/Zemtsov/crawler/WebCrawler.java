package ru.ifmo.ctddev.Zemtsov.crawler;

import com.sun.org.apache.regexp.internal.RE;
import com.sun.org.apache.xpath.internal.SourceTree;
import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
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
        //list of all sites
        List<String> downloaded = new ArrayList<>();
        Map<String, IOException> errors = new HashMap<>();
        LocalInfo localInfo = new LocalInfo();
        //Future objects for all threads
        BlockingQueue<Future<Result>> futures = new LinkedBlockingQueue<>();
        futures.add(downloadService.submit(createDownloadingCallable(futures, url, depth, localInfo)));
        while (!futures.isEmpty()) {
            Future<Result> future = futures.poll();
            try {
                Result result = future.get();
                //all fictive object would be skipped
                downloaded.addAll(result.getDownloaded());
                errors.putAll(result.getErrors());
            } catch (InterruptedException | ExecutionException e) {
                //ignoring
            }
        }
        return new Result(downloaded, errors);
    }

    private Callable<Result> createDownloadingCallable(final BlockingQueue<Future<Result>> futures, final String url, final int depth, final LocalInfo localInfo) {
        return () -> {
            List<String> downloaded = new ArrayList<>();
            Map<String, IOException> errors = new HashMap<>();
            String host = URLUtils.getHost(url);

            //flag for containing this url in map
            final boolean[] flag = {false};
            localInfo.URLMap.compute(url, (key, value) -> {
                if (value == null || !value) {
                    flag[0] = true;
                }
                return true;
            });

            //if this url was already downloaded we finish our work
            if (!flag[0]) return new Result(new ArrayList<>(), new HashMap<>());

            //checking ow many threads are working with current host
            int newVal = localInfo.downloadingPerHost.compute(host, (key, value) -> {
                if (value == null) {
                    return 1;
                }
                return value + 1;
            });
            if (newVal > perHost) {
                localInfo.downloadingPerHost.compute(host, (key, value) -> value - 1);
                localInfo.URLMap.put(url, false);
                Future<Result> future = downloadService.submit(createDownloadingCallable(futures, url, depth, localInfo));
                futures.put(future);
                return new Result(new ArrayList<>(), new HashMap<>());
            }

            //in this moment we can work with this site
            try {
                Document document = downloader.download(url);
                downloaded.add(url);
                localInfo.downloadingPerHost.compute(host, (key, value) -> value - 1);
                //pre: document was downloaded
                //callable for extracting, if depth > 1
                Callable<Result> extractCallable = () -> {
                    try {
                        if (depth > 1) {
                            List<String> links = document.extractLinks();
                            for (String string : links) {
                                futures.put(downloadService
                                        .submit(createDownloadingCallable(futures, string, depth - 1, localInfo)));
                            }
                        }
                    } catch (IOException e) {
                        errors.compute(url, (key, value) -> e);
                    }
                    return new Result(new ArrayList<>(), new HashMap<>());
                };
                futures.put(extractService.submit(extractCallable));
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

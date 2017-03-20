package ru.ifmo.ctddev.Zemtsov.concurrent;

import info.kgeorgiy.java.advanced.concurrent.ListIP;
import info.kgeorgiy.java.advanced.concurrent.ScalarIP;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Created by vlad on 19.03.17.
 */
public class IterativeParallelism implements ListIP {


    @Override
    public <T> T maximum(int i, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        return runThreads(i, list, (sublist) -> Collections.max(sublist, comparator), (results) -> Collections.max(results, comparator));
    }

    @Override
    public <T> T minimum(int i, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        return maximum(i, list, Collections.reverseOrder(comparator));
    }

    @Override
    public <T> boolean all(int i, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return runThreads(i, list,
                (sublist) -> sublist.stream().allMatch(predicate),
                (results) -> !results.contains(false));
    }

    @Override
    public <T> boolean any(int i, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return !all(i, list, predicate.negate());
    }

    @Override
    public String join(int i, List<?> list) throws InterruptedException {
        return runThreads(i, list,
                (sublist) -> sublist.stream().map(Object::toString).collect(Collectors.joining()),
                (results) -> results.stream().collect(Collectors.joining()));
    }

    @Override
    public <T> List<T> filter(int i, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return runThreads(i, list,
                (sublist) -> sublist.stream().filter(predicate).collect(Collectors.toList()),
                (results) -> results.stream().flatMap(List::stream).collect(Collectors.toList()));
    }

    @Override
    public <T, U> List<U> map(int i, List<? extends T> list, Function<? super T, ? extends U> function) throws InterruptedException {
        return runThreads(i, list,
                (sublist) -> sublist.stream().map(function).collect(Collectors.toList()),
                (results) -> results.stream().flatMap(List::stream).collect(Collectors.toList()));
    }

    private <T, U> U runThreads(int i, List<? extends T> list, Function<List<? extends T>, U> threadFunction, Function<List<U>, U> resultFunction) throws InterruptedException {
        ArrayList<Thread> threads = new ArrayList<>();
        ArrayList<U> results = new ArrayList<U>();
        int count = Integer.max(list.size() / i + (list.size() % i > 0 ? 1 : 0), 2);
        int numOfThreads = list.size() / count + (list.size() % count > 0 ? 1 : 0);
        for (int j = 0; j < numOfThreads; ++j) {
            results.add(null);
        }
        for (int j = 0; j < numOfThreads; ++j) {
            int lastIndex = ((j + 1) * count) <= list.size() ? ((j + 1) * count) : list.size();
            int finalJ = j;
            threads.add(new Thread(() -> {
                results.set(finalJ, threadFunction.apply(list.subList(finalJ * count, lastIndex)));
            }));
            threads.get(threads.size() - 1).start();
        }
        for (Thread thread : threads) {
            if (thread.isAlive()) {
                thread.join();
            }
        }
        return resultFunction.apply(results);
    }
}

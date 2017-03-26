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

    /**
     * Returns the maximum element of the given collection, according to the
     * order induced by the specified comparator. Number of threads used in
     * searching maximum is specified.
     *
     * @param i          number of threads
     * @param list       given list
     * @param comparator specified comparator
     * @param <T>        the class of the objects in the collection
     * @return maximum element of given list
     * @throws InterruptedException thrown when one of threads is waiting, sleeping, or otherwise occupied,
     *                              and the thread is interrupted, either before or during the activity.
     */
    @Override
    public <T> T maximum(int i, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        return runThreads(i, list, (sublist) -> Collections.max(sublist, comparator), (results) -> Collections.max(results, comparator));
    }

    /**
     * Returns the minimum element of the given collection, according to the
     * order induced by the specified comparator. Number of threads used in
     * searching minimum is specified.
     *
     * @param i          number of threads
     * @param list       given list
     * @param comparator specified comparator
     * @param <T>        the class of the objects in the collection
     * @return minimum element of given list
     * @throws InterruptedException thrown when one of threads is waiting, sleeping, or otherwise occupied,
     *                              and the thread is interrupted, either before or during the activity.
     */
    @Override
    public <T> T minimum(int i, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        return maximum(i, list, Collections.reverseOrder(comparator));
    }

    /**
     * Returns whether all elements of given list match the provided predicate.
     * Number of threads method can work with is specified.
     *
     * @param i         number of threads
     * @param list      given list
     * @param predicate provided predicate
     * @param <T>       the class of the objects in the collection
     * @return {@code true} if either all elements of the list match the
     * provided predicate
     * @throws InterruptedException thrown when one of threads is waiting, sleeping, or otherwise occupied,
     *                              and the thread is interrupted, either before or during the activity.
     */
    @Override
    public <T> boolean all(int i, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return runThreads(i, list,
                (sublist) -> sublist.stream().allMatch(predicate),
                (results) -> !results.contains(false));
    }

    /**
     * Returns whether any element of given list match the provided predicate.
     * Number of threads method can work with is specified.
     *
     * @param i         number of threads
     * @param list      given list
     * @param predicate provided predicate
     * @param <T>       the class of the objects in the collection
     * @return {@code true} if any all elements of the list match the
     * provided predicate
     * @throws InterruptedException thrown when one of threads is waiting, sleeping, or otherwise occupied,
     *                              and the thread is interrupted, either before or during the activity.
     */
    @Override
    public <T> boolean any(int i, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return !all(i, list, predicate.negate());
    }

    /**
     * Return a concatenation of string representations of objects from list.
     * Number of threads method can work with is specified.
     *
     * @param i    number of threads
     * @param list given list
     * @return a string representation of objects
     * @throws InterruptedException thrown when one of threads is waiting, sleeping, or otherwise occupied,
     *                              and the thread is interrupted, either before or during the activity.
     */
    @Override
    public String join(int i, List<?> list) throws InterruptedException {
        return runThreads(i, list,
                (sublist) -> sublist.stream().map(Object::toString).collect(Collectors.joining()),
                (results) -> results.stream().collect(Collectors.joining()));
    }

    /**
     * Returns a list consisting of the elements of given list that match
     * the given predicate. Number of threads method can work with is specified.
     *
     * @param i         number of threads
     * @param list      given list
     * @param predicate provided predicate
     * @param <T>       the class of the objects in the collection
     * @return a new list
     * @throws InterruptedException thrown when one of threads is waiting, sleeping, or otherwise occupied,
     *                              and the thread is interrupted, either before or during the activity.
     */
    @Override
    public <T> List<T> filter(int i, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return runThreads(i, list,
                (sublist) -> sublist.stream().filter(predicate).collect(Collectors.toList()),
                (results) -> results.stream().flatMap(List::stream).collect(Collectors.toList()));
    }

    /**
     * Returns a list consisting of the results of applying the given
     * function to the elements of given list. Number of threads method
     * can work with is specified.
     *
     * @param i        number of threads
     * @param list     given list
     * @param function provided function
     * @param <T>      the class of the objects in the collection
     * @return a new list
     * @throws InterruptedException thrown when one of threads is waiting, sleeping, or otherwise occupied,
     *                              and the thread is interrupted, either before or during the activity.
     */
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
            thread.join();
        }
        return resultFunction.apply(results);
    }
}

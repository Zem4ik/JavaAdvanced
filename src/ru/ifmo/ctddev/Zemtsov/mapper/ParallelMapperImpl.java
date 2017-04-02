package ru.ifmo.ctddev.Zemtsov.mapper;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.List;
import java.util.function.Function;

/**
 * Created by vlad on 27.03.17.
 */
public class ParallelMapperImpl implements ParallelMapper {

    ParallelMapperImpl(int threads) {

    }

    @Override
    public synchronized  <T, R> List<R> map(Function<? super T, ? extends R> function, List<? extends T> list) throws InterruptedException {
        return null;
    }

    @Override
    public void close() throws InterruptedException {

    }
}

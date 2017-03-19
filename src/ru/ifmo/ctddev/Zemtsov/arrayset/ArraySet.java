package ru.ifmo.ctddev.Zemtsov.arrayset;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ArraySet<E> extends AbstractSet<E> implements NavigableSet<E> {
    private List<E> list;
    private ArraySet<E> descendingSet;
    private final Comparator<? super E> comparator;

    public ArraySet() {
        this.comparator = null;
        list = new ArrayList<E>();
    }

    @Override
    public boolean contains(Object o) {
        return Collections.<E>binarySearch(list, (E) o, comparator) >= 0;
    }

    public ArraySet(Collection<? extends E> collection) {
        this.comparator = null;
        TreeSet<E> treeSet = new TreeSet<E>(collection);
        list = Collections.unmodifiableList(new ArrayList<E>(treeSet));
    }

    public ArraySet(Collection<? extends E> collection, Comparator<? super E> comparator) {
        this.comparator = comparator;
        TreeSet<E> treeSet = new TreeSet<E>(comparator);
        treeSet.addAll(collection);
        list = Collections.unmodifiableList(new ArrayList<E>(treeSet));
    }

    private ArraySet(List<E> list, Comparator<? super E> comparator, boolean sorted) {
        this.comparator = comparator;
        this.list = list;
    }

    private int lowerIndex(E o) {
        int pos = Collections.binarySearch(list, o, comparator);
        return pos < 0 ? -pos - 2 : pos - 1;
    }

    private int floorIndex(E o) {
        int pos = Collections.binarySearch(list, o, comparator);
        return pos < 0 ? -pos - 2 : pos;
    }

    private int ceilingIndex(E o) {
        int pos = Collections.binarySearch(list, o, comparator);
        return pos < 0 ? -pos - 1 : pos;
    }

    private int higherIndex(E o) {
        int pos = Collections.binarySearch(list, o, comparator);
        return pos < 0 ? -pos - 1 : pos + 1;
    }

    @Override
    public E lower(E o) {
        int index = lowerIndex(o);
        return index < 0 || index >= list.size() ? null : list.get(index);
    }

    @Override
    public E floor(E o) {
        int index = floorIndex(o);
        return index < 0 || index >= list.size() ? null : list.get(index);
    }

    @Override
    public E ceiling(E o) {
        int index = ceilingIndex(o);
        return index < 0 || index >= list.size() ? null : list.get(index);
    }

    @Override
    public E higher(E o) {
        int index = higherIndex(o);
        return index < 0 || index >= list.size() ? null : list.get(index);
    }

    @Override
    public E pollFirst() {
        throw new UnsupportedOperationException("ArraySet is immutable");
    }

    @Override
    public E pollLast() {
        throw new UnsupportedOperationException("ArraySet is immutable");
    }

    @Override
    public Iterator<E> iterator() {
        return list.iterator();
    }

    @Override
    public void forEach(Consumer action) {

    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        return list.removeIf(filter);
    }

    @Override
    public NavigableSet<E> descendingSet() {
        if (descendingSet == null) {
            ArrayList<E> descendingList = new ArrayList<E>(list);
            Collections.reverse(descendingList);
            descendingSet = new ArraySet<E>(descendingList, Collections.reverseOrder(comparator), true);
            descendingSet.descendingSet = this;
        }
        return descendingSet;
    }

    @Override
    public Iterator<E> descendingIterator() {
        return descendingSet.iterator();
    }

    @Override
    public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
        int from = fromInclusive ? ceilingIndex(fromElement) : higherIndex(fromElement);
        int to = (toInclusive ? floorIndex(toElement) : lowerIndex(toElement)) + 1;
        return new ArraySet<E>(list.subList(from, to < from ? from : to), comparator, true);
    }

    @Override
    public NavigableSet<E> headSet(E toElement, boolean inclusive) {
        int index = inclusive ? floorIndex(toElement) : lowerIndex(toElement);
        return new ArraySet<E>(list.subList(0, index + 1), comparator, true);
    }

    @Override
    public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
        int index = inclusive ? ceilingIndex(fromElement) : higherIndex(fromElement);
        return new ArraySet<E>(list.subList(index, list.size()), comparator, true);
    }

    @Override
    public Comparator<? super E> comparator() {
        return comparator;
    }

    @Override
    public SortedSet<E> subSet(E fromElement, E toElement) {
        return subSet(fromElement, true, toElement, false);
    }

    @Override
    public SortedSet<E> headSet(E toElement) {
        return headSet(toElement, false);
    }

    @Override
    public SortedSet<E> tailSet(E fromElement) {
        return tailSet(fromElement, true);
    }

    @Override
    public E first() {
        if (isEmpty()) {
            throw new NoSuchElementException("ArraySet is empty");
        }
        return list.get(0);
    }

    @Override
    public E last() {
        if (isEmpty()) {
            throw new NoSuchElementException("ArraySet is empty");
        }
        return list.get(list.size() - 1);
    }

    @Override
    public int size() {
        return list.size();
    }
}

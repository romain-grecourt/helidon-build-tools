/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.build.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * List utilities.
 */
@SuppressWarnings("unused")
public class Lists {

    private Lists() {
    }

    /**
     * Filter the elements of the given collection.
     *
     * @param list      input list
     * @param predicate predicate function
     * @param <T>       output element type
     * @return new list
     */
    public static <T> List<T> filter(Collection<T> list, Predicate<T> predicate) {
        return list == null ? List.of() : list.stream().filter(predicate).collect(Collectors.toList());
    }

    /**
     * Filter the elements of the given collection.
     *
     * @param list  input list
     * @param clazz type predicate
     * @param <T>   input element type
     * @param <V>   output element type
     * @return new list
     */
    public static <T, V> List<V> filter(Collection<T> list, Class<V> clazz) {
        return list == null ? List.of() : list.stream()
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .collect(Collectors.toList());
    }

    /**
     * Map the elements of the given list.
     *
     * @param list     input list
     * @param function mapping function
     * @param <T>      output element type
     * @param <V>      input element type
     * @return new list
     */
    public static <T, V> List<T> map(Collection<V> list, Function<V, T> function) {
        return list == null ? List.of() : list.stream().map(function).collect(Collectors.toList());
    }

    /**
     * Map the elements of the given list.
     *
     * @param stream   input stream
     * @param function mapping function
     * @param <T>      output element type
     * @param <V>      input element type
     * @return new list
     */
    public static <T, V> List<T> map(Stream<V> stream, Function<V, T> function) {
        return stream == null ? List.of() : stream.map(function).collect(Collectors.toList());
    }

    /**
     * Flat-map the elements of the given list.
     *
     * @param list     input list
     * @param function mapping function
     * @param <T>      output element type
     * @param <V>      input element type
     * @return new list
     */
    public static <T, V> List<T> flatMapStream(Collection<V> list, Function<V, Stream<T>> function) {
        return list == null ? List.of() : list.stream().flatMap(function).collect(Collectors.toList());
    }

    /**
     * Flat-map the elements of the given list.
     *
     * @param stream   input stream
     * @param function mapping function
     * @param <T>      output element type
     * @param <V>      input element type
     * @return new list
     */
    public static <T, V> List<T> flatMapStream(Stream<V> stream, Function<V, Stream<T>> function) {
        return stream == null ? List.of() : stream.flatMap(function).collect(Collectors.toList());
    }

    /**
     * Flat-map the elements of the given list.
     *
     * @param list     input list
     * @param function mapping function
     * @param <T>      output element type
     * @param <V>      input element type
     * @return new list
     */
    public static <T, V> List<T> flatMap(Collection<V> list, Function<V, Collection<T>> function) {
        return flatMapStream(list, e -> function.apply(e).stream());
    }

    /**
     * Flat-map the elements of the given stream.
     *
     * @param stream   input list
     * @param function mapping function
     * @param <T>      output element type
     * @param <V>      input element type
     * @return new list
     */
    public static <T, V> List<T> flatMap(Stream<V> stream, Function<V, Collection<T>> function) {
        return flatMapStream(stream, e -> function.apply(e).stream());
    }

    /**
     * Flat-map the elements of the given list.
     *
     * @param list input list
     * @param <T>  element type
     * @return new list
     */
    public static <T> List<T> flatMap(Collection<? extends Collection<T>> list) {
        return flatMapStream(list, Collection::stream);
    }

    /**
     * Concat the given lists.
     *
     * @param list1 list 1
     * @param list2 list 2
     * @param <T>   element type
     * @return new list
     */
    public static <T> List<T> addAll(Collection<T> list1, Collection<T> list2) {
        List<T> list = new ArrayList<>();
        if (list1 != null) {
            list.addAll(list1);
        }
        if (list2 != null) {
            list.addAll(list2);
        }
        return list;
    }

    /**
     * Concat the given lists.
     *
     * @param list1    list 1
     * @param elements elements elements
     * @param <T>      element type
     * @return new list
     */
    @SafeVarargs
    public static <T> List<T> addAll(Collection<T> list1, T... elements) {
        return addAll(list1, null, elements);
    }

    /**
     * Concat the given lists.
     *
     * @param list1    list 1
     * @param list2    list 2
     * @param elements elements elements
     * @param <T>      element type
     * @return new list
     */
    @SafeVarargs
    public static <T> List<T> addAll(Collection<T> list1, Collection<T> list2, T... elements) {
        List<T> list = new ArrayList<>();
        if (list1 != null) {
            list.addAll(list1);
        }
        if (list2 != null) {
            list.addAll(list2);
        }
        Collections.addAll(list, elements);
        return list;
    }

    /**
     * Concat the given lists.
     *
     * @param list1    list 1
     * @param index    index to insert elements at
     * @param elements elements elements
     * @param <T>      element type
     * @return new list
     */
    @SafeVarargs
    public static <T> List<T> addAll(Collection<T> list1, int index, T... elements) {
        List<T> list = new ArrayList<>();
        Iterator<T> it = list1.iterator();
        for (int i = 0; i < index && it.hasNext(); i++) {
            list.add(it.next());
        }
        for (int i = list.size(); i < index + elements.length; i++) {
            int offset = Math.max(0, i - index);
            list.add(elements[offset]);
        }
        while (it.hasNext()) {
            list.add(it.next());
        }
        return list;
    }

    /**
     * Create a new {@link ArrayList} with the given elements.
     *
     * @param elements input elements
     * @param <T>      element type
     * @return new list
     */
    @SafeVarargs
    public static <T> List<T> of(T... elements) {
        List<T> list = new ArrayList<>();
        Collections.addAll(list, elements);
        return list;
    }

    /**
     * Create a list from an iterable.
     *
     * @param iterable iterable
     * @param <T>      element type
     * @return new list
     */
    public static <T> List<T> of(Iterable<T> iterable) {
        List<T> list = new ArrayList<>();
        for (T element : iterable) {
            list.add(element);
        }
        return list;
    }

    /**
     * Create a list from an iterator.
     *
     * @param iterator iterator
     * @param <T>      element type
     * @return new list
     */
    public static <T> List<T> of(Iterator<T> iterator) {
        List<T> list = new ArrayList<>();
        while (iterator.hasNext()) {
            list.add(iterator.next());
        }
        return list;
    }

    /**
     * Map and join the given list.
     *
     * @param list      input list
     * @param function  mapping function
     * @param delimiter delimiter
     * @param <T>       element type
     * @return string
     */
    public static <T> String join(Collection<T> list, Function<T, Object> function, String delimiter) {
        return list.stream().map(function).map(Object::toString).collect(Collectors.joining(delimiter));
    }

    /**
     * Separate the given list into groups.
     *
     * @param list     input list
     * @param function grouping function
     * @param <T>      the list element type
     * @param <U>      key type
     * @return list of groups
     */
    public static <T, U> List<List<T>> groupingBy(Collection<T> list, Function<T, U> function) {
        return new ArrayList<>(list.stream().collect(Collectors.groupingBy(function)).values());
    }

    /**
     * Convert List to Map grouping values from the list by the function.
     *
     * @param list     input list
     * @param function grouping function
     * @param <T>      the list element type
     * @param <U>      key type
     * @return map where values grouped by keys
     */
    public static <T, U> Map<U, List<T>> mappedBy(Collection<T> list, Function<T, U> function) {
        return list.stream().collect(Collectors.groupingBy(function));
    }

    /**
     * Compare two lists.
     *
     * @param list1 list1
     * @param list2 list2
     * @param <T>   the list element type
     * @return {@code -1}, {@code 0}, or {@code 1} if {@code list1} is less than, equal to, or greater than {@code list2}.
     */
    public static <T extends Comparable<T>> int compare(Collection<T> list1, Collection<T> list2) {
        Iterator<T> it1 = list1.iterator();
        Iterator<T> it2 = list2.iterator();
        while (it1.hasNext() && it2.hasNext()) {
            T e1 = it1.next();
            T e2 = it2.next();
            int r = e1.compareTo(e2);
            if (r != 0) {
                return r;
            }
        }
        return it2.hasNext() ? -1 : it1.hasNext() ? 1 : 0;
    }

    /**
     * Sort the given collection.
     *
     * @param list collection
     * @param <T>  the list element type
     * @return sorted list
     */
    public static <T extends Comparable<T>> List<T> sorted(Collection<T> list) {
        return sorted(list, Comparator.naturalOrder());
    }

    /**
     * Sort the given collection.
     *
     * @param list       collection
     * @param comparator comparator
     * @param <T>        the list element type
     * @return sorted list
     */
    public static <T> List<T> sorted(Collection<T> list, Comparator<T> comparator) {
        List<T> sorted = new ArrayList<>(list);
        sorted.sort(comparator);
        return sorted;
    }

    /**
     * Find the first match between two lists.
     *
     * @param list1 list1
     * @param list2 list2
     * @param <T>   the list element type
     * @return last match, or {@code null} if none is found
     */
    public static <T> T firstMatch(Iterable<T> list1, Iterable<T> list2) {
        Iterator<T> it1 = list1.iterator();
        Iterator<T> it2 = list2.iterator();
        while (it1.hasNext() && it2.hasNext()) {
            T last = it1.next();
            if (last == it2.next()) {
                return last;
            }
        }
        return null;
    }

    /**
     * Test if any element matches the given predicate.
     *
     * @param list      list
     * @param predicate predicate
     * @param <T>       the list element type
     * @return {@code true} if any element matches, {@code false} otherwise
     */
    public static <T> boolean anyMatch(Iterable<T> list, Predicate<T> predicate) {
        for (T t : list) {
            if (predicate.test(t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Test if all elements match the given predicate.
     *
     * @param list      list
     * @param predicate predicate
     * @param <T>       the list element type
     * @return {@code true} if any element matches, {@code false} otherwise
     */
    public static <T> boolean allMatch(Iterable<T> list, Predicate<T> predicate) {
        for (T t : list) {
            if (!predicate.test(t)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Test if the given list contains the given case-insensitive value.
     *
     * @param list  list
     * @param value value
     * @return {@code true} if the value was found, {@code false} otherwise
     */
    public static boolean containsIgnoreCase(List<String> list, String value) {
        if (list == null) {
            return false;
        }
        for (String v : list) {
            if (v.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Test if the given element is the last of the given list.
     *
     * @param list    list
     * @param element element
     * @param <T>     the list element type
     * @return {@code true} if last, {@code false} otherwise
     */
    public static <T> boolean isLast(List<T> list, T element) {
        return list.indexOf(element) == list.size() - 1;
    }
}

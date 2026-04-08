/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toCollection;

/**
 * List utilities.
 */
@SuppressWarnings("unused")
public class Lists {

    private Lists() {
    }

    /**
     * Filter the elements of the given collection into an {@link ArrayList}.
     *
     * @param list      input list
     * @param predicate predicate function
     * @param <T>       output element type
     * @return new list
     */
    public static <T> List<T> filter(Collection<T> list, Predicate<T> predicate) {
        return list == null ? List.of() : list.stream()
                .filter(predicate)
                .collect(toCollection(ArrayList::new));
    }

    /**
     * Filter the elements of the given collection into an {@link ArrayList}.
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
                .collect(toCollection(ArrayList::new));
    }

    /**
     * Map the elements of the given list into an {@link ArrayList}.
     *
     * @param list     input list
     * @param function mapping function
     * @param <T>      output element type
     * @param <V>      input element type
     * @return new list
     */
    public static <T, V> List<T> map(Collection<V> list, Function<V, T> function) {
        return list == null ? List.of() : list.stream()
                .map(function)
                .collect(toCollection(ArrayList::new));
    }

    /**
     * Flat-map the elements of the given list into an {@link ArrayList}.
     *
     * @param list input list
     * @param <T>  output element type
     * @param <V>  input element type
     * @return new list
     */
    public static <T, V extends Collection<T>> List<T> flatMap(Collection<V> list) {
        return list == null ? List.of() : list.stream()
                .flatMap(Collection::stream)
                .collect(toCollection(ArrayList::new));
    }

    /**
     * Flat-map the elements of the given list into an {@link ArrayList} of distinct elements.
     *
     * @param list input list
     * @param <T>  output element type
     * @param <V>  input element type
     * @return new list
     */
    public static <T, V extends Collection<T>> List<T> flatMapDistinct(Collection<V> list) {
        return list == null ? List.of() : list.stream()
                .flatMap(Collection::stream)
                .distinct()
                .collect(toCollection(ArrayList::new));
    }

    /**
     * Flat-map the elements of the given list into an {@link ArrayList}.
     *
     * @param list     input list
     * @param function mapping function
     * @param <T>      output element type
     * @param <V>      input element type
     * @return new list
     */
    public static <T, V> List<T> flatMap(Collection<V> list, Function<V, Collection<T>> function) {
        return list == null ? List.of() : list.stream()
                .flatMap(e -> function.apply(e).stream())
                .collect(toCollection(ArrayList::new));
    }

    /**
     * Concat the given lists into an {@link ArrayList}.
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
     * Concat the given lists into an {@link ArrayList}.
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
     * Concat the given lists into an {@link ArrayList}.
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
     * Concat the given lists into an {@link ArrayList}.
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
     * Create an {@link ArrayList} with the given elements.
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
     * Create an {@link ArrayList} from an iterable.
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
     * Create an {@link ArrayList} from an iterator.
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
     * Create a new list without the element at the given index.
     *
     * @param list  input list
     * @param index index to remove
     * @param <T>   element type
     * @return new list
     */
    public static <T> List<T> withoutIndex(List<T> list, int index) {
        List<T> filtered = new ArrayList<>(list.size() - 1);
        filtered.addAll(list.subList(0, index));
        filtered.addAll(list.subList(index + 1, list.size()));
        return filtered;
    }

    /**
     * Map and join the given list.
     *
     * @param list      input list
     * @param delimiter delimiter
     * @param <T>       element type
     * @return string
     */
    public static <T> String join(Collection<T> list, String delimiter) {
        return join(list, t -> t, delimiter);
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
     * @param <T>      list element type
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
        return compare(list1, list2, Comparable::compareTo);
    }

    /**
     * Compare two lists.
     *
     * @param list1      list1
     * @param list2      list2
     * @param comparator comparator
     * @param <T>        the list element type
     * @return {@code -1}, {@code 0}, or {@code 1} if {@code list1} is less than, equal to, or greater than {@code list2}.
     */
    public static <T> int compare(Collection<T> list1, Collection<T> list2, Comparator<T> comparator) {
        Iterator<T> it1 = list1.iterator();
        Iterator<T> it2 = list2.iterator();
        while (it1.hasNext() && it2.hasNext()) {
            T e1 = it1.next();
            T e2 = it2.next();
            int r = comparator.compare(e1, e2);
            if (r != 0) {
                return r;
            }
        }
        return it2.hasNext() ? -1 : it1.hasNext() ? 1 : 0;
    }

    /**
     * Remove adjacent duplicates from a sorted list.
     *
     * @param list sorted list
     * @param <T>  the list element type
     * @return list without adjacent duplicates
     */
    public static <T> List<T> unique(List<T> list) {
        List<T> result = new ArrayList<>(list.size());
        T previous = null;
        for (T element : list) {
            if (!Objects.equals(element, previous)) {
                result.add(element);
            }
            previous = element;
        }
        return result;
    }

    /**
     * Test if all elements from a sorted subset list are present in the sorted superset list.
     *
     * @param superset sorted superset list
     * @param subset   sorted subset list
     * @param <T>      the list element type
     * @return {@code true} if all subset elements are present, {@code false} otherwise
     */
    public static <T extends Comparable<T>> boolean containsAll(List<T> superset, List<T> subset) {
        int i = 0;
        int j = 0;
        while (i < superset.size() && j < subset.size()) {
            int cmp = superset.get(i).compareTo(subset.get(j));
            if (cmp == 0) {
                i++;
                j++;
            } else if (cmp < 0) {
                i++;
            } else {
                return false;
            }
        }
        return j == subset.size();
    }

    /**
     * Test if two collections share at least one element.
     *
     * @param left left collection
     * @param right right collection
     * @param <T> the collection element type
     * @return {@code true} if the collections intersect, {@code false} otherwise
     */
    public static <T> boolean intersects(Collection<T> left, Collection<T> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        Collection<T> smaller = left.size() <= right.size() ? left : right;
        Collection<T> larger = smaller == left ? right : left;
        for (T element : smaller) {
            if (larger.contains(element)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Intersect two sorted lists.
     *
     * @param left left list
     * @param right right list
     * @param <T> the list element type
     * @return intersection preserving sorted order
     */
    public static <T extends Comparable<T>> List<T> intersect(List<T> left, List<T> right) {
        List<T> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < left.size() && j < right.size()) {
            int cmp = left.get(i).compareTo(right.get(j));
            if (cmp == 0) {
                result.add(left.get(i));
                i++;
                j++;
            } else if (cmp < 0) {
                i++;
            } else {
                j++;
            }
        }
        return result;
    }

    /**
     * Subtract the sorted exclusions list from the sorted values list.
     *
     * @param values sorted values list
     * @param exclusions sorted exclusions list
     * @param <T> the list element type
     * @return values minus exclusions preserving sorted order
     */
    public static <T extends Comparable<T>> List<T> subtract(List<T> values, List<T> exclusions) {
        List<T> result = new ArrayList<>(Math.max(0, values.size() - exclusions.size()));
        int i = 0;
        int j = 0;
        while (i < values.size()) {
            if (j >= exclusions.size()) {
                result.addAll(values.subList(i, values.size()));
                break;
            }
            int cmp = values.get(i).compareTo(exclusions.get(j));
            if (cmp == 0) {
                i++;
                j++;
            } else if (cmp < 0) {
                result.add(values.get(i++));
            } else {
                j++;
            }
        }
        return result;
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
     * @return first match, or {@code null} if none is found
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
     * Get the index of the given case-insensitive value.
     *
     * @param list  list
     * @param value value
     * @return index, or {@code -1} if not found
     */
    public static int indexOfIgnoreCase(List<String> list, String value) {
        ListIterator<String> it = list.listIterator();
        while (it.hasNext()) {
            if (it.next().equalsIgnoreCase(value)) {
                return it.previousIndex();
            }
        }
        return -1;
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

    /**
     * Compute a max from a list.
     *
     * @param list     list
     * @param function function
     * @param <T>      the list element type
     * @return max
     */
    public static <T> int max(Collection<T> list, Function<T, Integer> function) {
        return list.stream().mapToInt(function::apply).max().orElseThrow();
    }

    /**
     * Drain the given list.
     *
     * @param list list
     * @param <T>  the list element type
     * @return drain elements
     */
    public static <T> List<T> drain(Collection<T> list) {
        List<T> drained = new ArrayList<>(list);
        list.clear();
        return drained;
    }
}

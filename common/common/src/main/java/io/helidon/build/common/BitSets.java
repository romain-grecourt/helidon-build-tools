/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import java.util.BitSet;

/**
 * A fluent {@link BitSet} utility.
 */
public final class BitSets {

    private BitSets() {
    }

    /**
     * Test if all the given bits are set.
     *
     * @param bs1 bits
     * @param bs2 bits
     * @return {@code true} if all bits are set
     */
    public static boolean containsAll(BitSet bs1, BitSet bs2) {
        return and(copyOf(bs1), bs2).equals(bs1);
    }

    /**
     * Create a copy.
     *
     * @param bits bits
     * @return BitSet
     */
    public static BitSet copyOf(BitSet bits) {
        BitSet bs = new BitSet();
        bs.or(bits);
        return bs;
    }

    /**
     * Create a new instance.
     *
     * @param bits bits
     * @return BitSet
     */
    public static BitSet of(int... bits) {
        BitSet bs = new BitSet();
        for (int bit : bits) {
            bs.set(bit);
        }
        return bs;
    }

    /**
     * Create a new instance.
     *
     * @param words words
     * @return BitSet
     */
    public static BitSet of(long... words) {
        return BitSet.valueOf(words);
    }

    /**
     * See {@link BitSet#or(BitSet)}.
     *
     * @param bs1 bits
     * @param bs2 bits
     * @return BitSet
     */
    public static BitSet or(BitSet bs1, BitSet bs2) {
        bs1.or(bs2);
        return bs1;
    }

    /**
     * See {@link BitSet#and(BitSet)}.
     *
     * @param bs1 bits
     * @param bs2 bits
     * @return BitSet
     */
    public static BitSet and(BitSet bs1, BitSet bs2) {
        bs1.and(bs2);
        return bs1;
    }

    /**
     * See {@link BitSet#andNot(BitSet)}.
     *
     * @param bs1 bits
     * @param bs2 bits
     * @return BitSet
     */
    public static BitSet andNot(BitSet bs1, BitSet bs2) {
        bs1.andNot(bs2);
        return bs1;
    }

    /**
     * Create the bounded complement of the given bits.
     *
     * @param bits bits
     * @param size exclusive upper bound
     * @return BitSet
     */
    public static BitSet not(BitSet bits, int size) {
        BitSet result = new BitSet();
        result.set(0, size);
        result.andNot(bits);
        return result;
    }

    /**
     * Return the ordinal positions from {@code bits} whose bit values are also set in {@code matches}.
     *
     * @param bits source bits
     * @param matches matching bits
     * @return BitSet
     */
    public static BitSet indicesOf(BitSet bits, BitSet matches) {
        BitSet result = new BitSet();
        for (int i = 0, bit = bits.nextSetBit(0); bit >= 0; bit = bits.nextSetBit(bit + 1), i++) {
            if (matches.get(bit)) {
                result.set(i);
            }
        }
        return result;
    }

    /**
     * Reindex the given bits after excluding the given ordinal positions.
     *
     * @param bits source bits
     * @param excluded excluded ordinal positions
     * @return BitSet
     */
    public static BitSet reindex(BitSet bits, BitSet excluded) {
        BitSet result = new BitSet();
        for (int i = excluded.nextClearBit(0), index = 0; i >= 0 && i < bits.length(); i = excluded.nextClearBit(i + 1)) {
            if (bits.get(i)) {
                result.set(index);
            }
            index++;
        }
        return result;
    }

    /**
     * Count the bits set in both bitsets.
     *
     * @param bs1 bits
     * @param bs2 bits
     * @return number of intersecting bits
     */
    public static int intersectCount(BitSet bs1, BitSet bs2) {
        int count = 0;
        for (int bit = bs1.nextSetBit(0); bit >= 0; bit = bs1.nextSetBit(bit + 1)) {
            if (bs2.get(bit)) {
                count++;
            }
        }
        return count;
    }
}

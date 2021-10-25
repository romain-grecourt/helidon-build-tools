/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.build.archetype.engine.v2.ast;

import io.helidon.build.archetype.engine.v2.interpreter.Visitor;

import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Base class for AST nodes.
 */
public abstract class Node implements Serializable {

    private final List<Node> children = new LinkedList<>();
    private Node parent;
    private final Location location;
    private Iterator<Node> iterator;

    Node(Node parent, Location location) {
        this.location = location;
        this.parent = parent;
    }

    /**
     * Returns true if the node has more children.
     *
     * @return true if the node has more children, false otherwise
     */
    public boolean hasNext() {
        if (iterator == null) {
            iterator = children.iterator();
        }
        return iterator.hasNext();
    }

    /**
     * Returns the next child.
     *
     * @return next child
     */
    public Node next() {
        if (iterator == null) {
            iterator = children.iterator();
        }
        return iterator.next();
    }

    /**
     * Location associated with the current node.
     *
     * @return location
     */
    public Location location() {
        return location;
    }

    /**
     * Get children nodes of the current node.
     *
     * @return children nodes
     */
    public List<Node> children() {
        return children;
    }

    public <T> Stream<T> childrenOf(Class<T> type) {
        return children.stream().filter(type::isInstance).map(type::cast);
    }

    /**
     * Parent node for the current node.
     *
     * @return parent
     */
    public Node parent() {
        return parent;
    }

    public void parent(Node parent) {
        this.parent = parent;
    }

    /**
     * Visit a node.
     *
     * @param visitor Visitor
     * @param arg     argument
     * @param <R>     generic type of the result
     * @param <A>     generic type of the arguments
     * @return result
     */
    public abstract <A, R> R accept(Visitor<A, R> visitor, A arg);
}

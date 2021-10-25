package io.helidon.build.archetype.engine.v2.ast;

/**
 * Base class for descriptor based AST nodes.
 *
 * @param <T> descriptor type
 */
public abstract class DescriptorNode<T extends Object> extends Node {

    private final T descriptor;

    /**
     * Create a new descriptor node.
     *
     * @param descriptor descriptor
     * @param parent     parent node
     * @param location   location
     */
    protected DescriptorNode(T descriptor, Node parent, Location location) {
        super(parent, location);
        this.descriptor = descriptor;
    }

    /**
     * Get the descriptor.
     *
     * @return descriptor
     */
    public T descriptor() {
        return descriptor;
    }
}

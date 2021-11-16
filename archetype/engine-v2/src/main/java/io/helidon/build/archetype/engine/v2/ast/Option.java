package io.helidon.build.archetype.engine.v2.ast;

/**
 * Option.
 */
public final class Option extends BlockStatement {

    private final String label;
    private final String value;

    private Option(Builder builder) {
        super(builder);
        this.label = builder.label;
        this.value = builder.value;
    }

    /**
     * Get the value.
     *
     * @return value
     */
    public String value() {
        return value;
    }

    /**
     * Get the label.
     *
     * @return label
     */
    public String label() {
        return label;
    }

    @Override
    public <A, R> R accept(Visitor<A, R> visitor, A arg) {
        return visitor.visit(this, arg);
    }

    /**
     * Create a new builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Option builder.
     */
    public static final class Builder extends BlockStatement.Builder<Option, Builder> {

        private String label;
        private String value;

        private Builder() {
        }

        /**
         * Set the label.
         *
         * @param label label
         * @return this builder
         */
        public Builder label(String label) {
            this.label = label;
            return this;
        }

        /**
         * Set the value.
         *
         * @param value value
         * @return this builder
         */
        public Builder value(String value) {
            this.value = value;
            return this;
        }

        @Override
        public Option build() {
            return new Option(this);
        }
    }
}

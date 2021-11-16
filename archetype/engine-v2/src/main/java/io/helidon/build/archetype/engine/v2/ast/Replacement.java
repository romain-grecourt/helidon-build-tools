package io.helidon.build.archetype.engine.v2.ast;

import java.util.Objects;

public class Replacement extends Statement {

    private final String regex;
    private final String replacement;

    private Replacement(Builder builder) {
        super(builder);
        this.regex = Objects.requireNonNull(builder.regex, () -> "regex is null, " + position);
        this.replacement = Objects.requireNonNull(builder.replacement, ()-> "replacement is null, " + position);
    }

    /**
     * Get the regex.
     *
     * @return regex
     */
    public String regex() {
        return regex;
    }

    /**
     * Get the replacement.
     *
     * @return replacement
     */
    public String replacement() {
        return replacement;
    }

    @Override
    public <A, R> R accept(Visitor<A, R> visitor, A arg) {
        return visitor.visit(this, arg);
    }

    /**
     * Create a new builder.
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Replacement builder.
     */
    @SuppressWarnings("unchecked")
    public static final class Builder extends Statement.Builder<Replacement, Builder> {

        private String regex;
        private String replacement;

        private Builder(){
        }

        /**
         * Set the regex.
         *
         * @param regex regex
         * @return this builder
         */
        public Builder regex(String regex) {
            this.regex = regex;
            return this;
        }

        /**
         * Set the replacement.
         *
         * @param replacement replacement
         * @return this builder
         */
        public Builder replacement(String replacement) {
            this.replacement = replacement;
            return this;
        }

        @Override
        public Replacement build() {
            return new Replacement(this);
        }
    }
}

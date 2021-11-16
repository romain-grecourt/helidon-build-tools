package io.helidon.build.archetype.engine.v2.ast;

import java.util.Objects;

public class Help extends Statement {

    private final String help;

    private Help(Builder builder) {
        super(builder);
        this.help = Objects.requireNonNull(builder.help, () -> "help is null, " + position);
    }

    /**
     * Get the help.
     *
     * @return help
     */
    public String help() {
        return help;
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
     * Replacement builder.
     */
    @SuppressWarnings("unchecked")
    public static final class Builder extends Statement.Builder<Help, Builder> {

        private String help;

        private Builder() {
        }

        /**
         * Set the help.
         *
         * @param help help
         * @return this builder
         */
        public Builder help(String help) {
            this.help = help;
            return this;
        }

        @Override
        public Help build() {
            return new Help(this);
        }
    }
}

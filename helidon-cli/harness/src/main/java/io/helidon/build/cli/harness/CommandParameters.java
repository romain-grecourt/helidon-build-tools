package io.helidon.build.cli.harness;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Model common to {@link Command} and {@link CommandFragment}.
 */
public class CommandParameters {

    private final List<ParameterInfo> params;

    protected CommandParameters() {
        this.params = new LinkedList<>();
    }

    /**
     * Add an attribute to the command model.
     *
     * @param param parameter info to add
     */
    protected final void addParameter(ParameterInfo param) {
        params.add(Objects.requireNonNull(param, "param is null"));
    }

    /**
     * Get the parameters for this model.
     *
     * @return list of {@link ParameterInfo}, never {@code null}
     */
    public final List<ParameterInfo> parameters() {
        return params;
    }

    /**
     * Meta model for parameters to retain the mapped type.
     * @param <T> mapped type
     */
    public interface ParameterInfo<T> {

        /**
         * The parameter type.
         * @return type
         */
        Class<T> type();

        /**
         * Indicate if the parameter is visible.
         *
         * @return {@code true} if visible, {@code false} if not visible.
         */
        default boolean visible() {
            return true;
        }
    }

    /**
     * Base class for meta-model implementations of {@link CommandFragment}.
     * @param <T> mapped type
     */
    public static abstract class CommandFragmentInfo<T> extends CommandParameters implements ParameterInfo<T> {

        private final Class<T> type;

        /**
         * Create a new fragment info.
         *
         * @param type fragment type
         */
        protected CommandFragmentInfo(Class<T> type) {
            super();
            this.type = Objects.requireNonNull(type, "type is null");
        }

        @Override
        public final Class<T> type() {
            return type;
        }

        /**
         * Resolve a fragment instance.
         * @param parser command parser
         * @return created fragment
         */
        public abstract T resolve(CommandParser parser);
    }
}

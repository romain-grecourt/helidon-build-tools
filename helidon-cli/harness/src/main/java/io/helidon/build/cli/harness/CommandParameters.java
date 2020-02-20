package io.helidon.build.cli.harness;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
     * Get the parameter at the given idx with the given type.
     *
     * @param <T> parameter type
     * @param type actual type
     * @param idx index
     * @return parameter info
     */
    @SuppressWarnings("unchecked")
    protected final <T> ParameterInfo<T> param(Class<T> type, int idx) {
        ParameterInfo param = parameters().get(idx);
        if (param.type().equals(type)) {
            return (ParameterInfo<T>) param;
        }
        throw new IllegalStateException("Parameter mismatch, class=" + this.getClass() 
                + ", index=" + idx + ", expectedType=" + type + ", actualType=" + param.type());
    }

    /**
     * Meta model for parameters to retain the mapped type.
     * @param <T> mapped type
     */
    public interface ParameterInfo<T> {

        /**
         * The parameter type.
         * @return type, never {@code null}
         */
        Class<T> type();

        // TODO typeParam
    }

    /**
     * Base class for meta-model implementations of {@link CommandFragment}.
     * @param <T> mapped type
     */
    public static abstract class CommandFragmentInfo<T> extends CommandParameters implements ParameterInfo<T> {

        private final Class<T> type;
        private final Map<CommandParser, T> fragmentsCache;

        /**
         * Create a new fragment info.
         *
         * @param type fragment type
         */
        protected CommandFragmentInfo(Class<T> type) {
            super();
            this.type = Objects.requireNonNull(type, "type is null");
            fragmentsCache = new HashMap<>();
        }

        /**
         * Get the fragment for the given parser from the cache or create it if not cached.
         * @param parser command parser
         * @return fragment
         */
        public final T getOrCreate(CommandParser parser) {
            T fragment = fragmentsCache.get(parser);
            if (fragment == null) {
                fragment = create(parser);
                fragmentsCache.put(parser, fragment);
            }
            return fragment;
        }

        @Override
        public final Class<T> type() {
            return type;
        }

        /**
         * Create a fragment instance.
         * @param parser command parser
         * @return created fragment
         */
        public abstract T create(CommandParser parser);
    }
}

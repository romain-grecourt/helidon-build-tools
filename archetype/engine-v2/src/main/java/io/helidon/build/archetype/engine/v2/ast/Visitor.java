package io.helidon.build.archetype.engine.v2.ast;

/**
 * Visitor.
 *
 * @param <A> argument
 * @param <R> type of the returned value
 */
public interface Visitor<A, R> {

    default R visit(Statement stmt, A arg) {
        if (stmt instanceof IfStatement) {
            return visit((IfStatement) stmt, arg);
        }
        if (stmt instanceof Help) {
            return visit((Help) stmt, arg);
        }
        if (stmt instanceof Invocation) {
            return visit((Invocation) stmt, arg);
        }
        if (stmt instanceof InputValue) {
            return visit((InputValue<?>) stmt, arg);
        }
        if (stmt instanceof AbstractFiles) {
            return visit((AbstractFiles) stmt, arg);
        }
        if (stmt instanceof Replacement) {
            return visit((Replacement) stmt, arg);
        }
        if (stmt instanceof BlockStatement) {
            return visit((BlockStatement) stmt, arg);
        }
        return null;
    }

    /**
     * Visit a block statement.
     *
     * @param block node
     * @param arg   argument
     * @return visit result
     */
    default R visit(BlockStatement block, A arg) {
        if (block instanceof ModelValue) {
            return visit((ModelValue) block, arg);
        }
        if (block instanceof Transformation) {
            return visit((Transformation) block, arg);
        }
        if (block instanceof Option) {
            return visit((Option) block, arg);
        }
        if (block instanceof Inputs) {
            return visit((Inputs) block, arg);
        }
        if (block instanceof Input) {
            return visit((Input) block, arg);
        }
        if (block instanceof Output) {
            return visit((Output) block, arg);
        }
        if (block instanceof Step) {
            return visit((Step) block, arg);
        }
        if (block instanceof Script) {
            return visit((Script) block, arg);
        }
        if (block instanceof InputValues) {
            return visit((InputValues) block, arg);
        }
        if (block instanceof Model) {
            return visit((Model) block, arg);
        }
        return null;
    }

    /**
     * Visit an if statement.
     *
     * @param ifStatement node
     * @param arg         argument
     * @return visit result
     */
    default R visit(IfStatement ifStatement, A arg) {
        return null;
    }

    /**
     * Visit input values.
     *
     * @param inputValues node
     * @param arg         argument
     * @return visit result
     */
    default R visit(InputValues inputValues, A arg) {
        inputValues.statements().forEach(stmt -> visit(stmt, arg));
        return null;
    }

    /**
     * Visit a boolean input value.
     *
     * @param inputValue node
     * @param arg        argument
     * @return visit result
     */
    default R visit(InputValue<?> inputValue, A arg) {
        if (inputValue instanceof BooleanInputValue) {
            return visit((BooleanInputValue) inputValue, arg);
        }
        if (inputValue instanceof TextInputValue) {
            return visit((TextInputValue) inputValue, arg);
        }
        if (inputValue instanceof EnumInputValue) {
            return visit((EnumInputValue) inputValue, arg);
        }
        if (inputValue instanceof ListInputValue) {
            return visit((ListInputValue) inputValue, arg);
        }
        return null;
    }

    /**
     * Visit a boolean input value.
     *
     * @param booleanInputValue node
     * @param arg               argument
     * @return visit result
     */
    default R visit(BooleanInputValue booleanInputValue, A arg) {
        return null;
    }

    /**
     * Visit a text input value.
     *
     * @param textInputValue node
     * @param arg            argument
     * @return visit result
     */
    default R visit(TextInputValue textInputValue, A arg) {
        return null;
    }

    /**
     * Visit a list input value.
     *
     * @param listInputValue node
     * @param arg            argument
     * @return visit result
     */
    default R visit(ListInputValue listInputValue, A arg) {
        return null;
    }

    /**
     * Visit an enum input value.
     *
     * @param enumInputValue node
     * @param arg            argument
     * @return visit result
     */
    default R visit(EnumInputValue enumInputValue, A arg) {
        return null;
    }

    /**
     * Visit a step.
     *
     * @param step node
     * @param arg  argument
     * @return visit result
     */
    default R visit(Step step, A arg) {
        step.statements().forEach(stmt -> visit(stmt, arg));
        return null;
    }

    /**
     * Visit inputs.
     *
     * @param inputs node
     * @param arg    argument
     * @return visit result
     */
    default R visit(Inputs inputs, A arg) {
        inputs.statements().forEach(stmt -> visit(stmt, arg));
        return null;
    }

    /**
     * Visit a boolean input.
     *
     * @param input node
     * @param arg   argument
     * @return visit result
     */
    default R visit(Input input, A arg) {
        if (input instanceof BooleanInput) {
            return visit((BooleanInput) input, arg);
        }
        if (input instanceof TextInput) {
            return visit((TextInput) input, arg);
        }
        if (input instanceof ListInput) {
            return visit((ListInput) input, arg);
        }
        if (input instanceof EnumInput) {
            return visit((EnumInput) input, arg);
        }
        return null;
    }

    /**
     * Visit a boolean input.
     *
     * @param booleanInput node
     * @param arg          argument
     * @return visit result
     */
    default R visit(BooleanInput booleanInput, A arg) {
        return null;
    }

    /**
     * Visit a text input.
     *
     * @param textInput node
     * @param arg       argument
     * @return visit result
     */
    default R visit(TextInput textInput, A arg) {
        return null;
    }

    /**
     * Visit a list input.
     *
     * @param listInput node
     * @param arg       argument
     * @return visit result
     */
    default R visit(ListInput listInput, A arg) {
        return null;
    }

    /**
     * Visit a list input.
     *
     * @param enumInput node
     * @param arg       argument
     * @return visit result
     */
    default R visit(EnumInput enumInput, A arg) {
        return null;
    }

    /**
     * Visit an option.
     *
     * @param option node
     * @param arg    argument
     * @return visit result
     */
    default R visit(Option option, A arg) {
        option.statements().forEach(stmt -> visit(stmt, arg));
        return null;
    }

    /**
     * Visit a script.
     *
     * @param script node
     * @param arg    argument
     * @return visit result
     */
    default R visit(Script script, A arg) {
        script.statements().forEach(stmt -> visit(stmt, arg));
        return null;
    }

    /**
     * Visit an invocation.
     *
     * @param invocation node
     * @param arg        argument
     * @return visit result
     */
    default R visit(Invocation invocation, A arg) {
        return null;
    }

    /**
     * Visit an output.
     *
     * @param output node
     * @param arg    argument
     * @return visit result
     */
    default R visit(Output output, A arg) {
        output.statements().forEach(stmt -> visit(stmt, arg));
        return null;
    }

    /**
     * Visit a transformation.
     *
     * @param transformation node
     * @param arg            argument
     * @return visit result
     */
    default R visit(Transformation transformation, A arg) {
        transformation.statements().forEach(stmt -> visit(stmt, arg));
        return null;
    }

    /**
     * Visit templates.
     *
     * @param templates node
     * @param arg      argument
     * @return visit result
     */
    default R visit(Templates template, A arg) {
        return null;
    }

    /**
     * Visit a file.
     *
     * @param file node
     * @param arg  argument
     * @return visit result
     */
    default R visit(AbstractFiles file, A arg) {
        if (file instanceof Files) {
            return visit((Files) file, arg);
        }
        if (file instanceof Templates) {
            return visit((Templates) file, arg);
        }
        return null;
    }

    /**
     * Visit a file.
     *
     * @param files node
     * @param arg   argument
     * @return visit result
     */
    default R visit(Files files, A arg) {
        return null;
    }

    /**
     * Visit a model.
     *
     * @param model node
     * @param arg   argument
     * @return visit result
     */
    default R visit(Model model, A arg) {
        model.statements().forEach(stmt -> visit(stmt, arg));
        return null;
    }

    /**
     * Visit a model value.
     *
     * @param modelValue node
     * @param arg        argument
     * @return visit result
     */
    default R visit(ModelValue modelValue, A arg) {
        if (modelValue instanceof ModelStringValue) {
            return visit((ModelStringValue) modelValue, arg);
        }
        if (modelValue instanceof ModelListValue) {
            return visit((ModelListValue) modelValue, arg);
        }
        if (modelValue instanceof ModelMapValue) {
            return visit((ModelMapValue) modelValue, arg);
        }
        return null;
    }

    /**
     * Visit a model string value.
     *
     * @param modelStringValue node
     * @param arg              argument
     * @return visit result
     */
    default R visit(ModelStringValue modelStringValue, A arg) {
        return null;
    }

    /**
     * Visit a model list value.
     *
     * @param modelListValue node
     * @param arg            argument
     * @return visit result
     */
    default R visit(ModelListValue modelListValue, A arg) {
        modelListValue.statements().forEach(stmt -> visit(stmt, arg));
        return null;
    }

    /**
     * Visit a model map value.
     *
     * @param modelMapValue node
     * @param arg           argument
     * @return visit result
     */
    default R visit(ModelMapValue modelMapValue, A arg) {
        modelMapValue.statements().forEach(stmt -> visit(stmt, arg));
        return null;
    }

    /**
     * Visit a replacement.
     *
     * @param replacement node
     * @param arg         argument
     * @return visit result
     */
    default R visit(Replacement replacement, A arg) {
        return null;
    }

    /**
     * Visit a help text.
     *
     * @param help node
     * @param arg  argument
     * @return visit result
     */
    default R visit(Help help, A arg) {
        return null;
    }
}

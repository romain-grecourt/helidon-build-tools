package io.helidon.build.cli.codegen;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.lang.model.util.SimpleElementVisitor9;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.Iterator;

import io.helidon.build.cli.harness.Argument;
import io.helidon.build.cli.harness.CommandRegistry;
import io.helidon.build.cli.harness.Command;
import io.helidon.build.cli.harness.CommandExecution;
import io.helidon.build.cli.harness.CommandModel.CommandInfo;
import io.helidon.build.cli.harness.Option;
import io.helidon.build.cli.harness.CommandFragment;
import io.helidon.build.cli.harness.CommandModel;
import io.helidon.build.cli.harness.CommandParameters;
import io.helidon.build.cli.harness.CommandParser;
import io.helidon.build.cli.harness.Creator;
import java.util.stream.Collectors;

/**
 * Command annotation processor.
 */
@SupportedAnnotationTypes(value = {
    "io.helidon.build.cli.harness.Command",
    "io.helidon.build.cli.harness.CommandFragment",
})
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class CommandAnnotationProcessor extends AbstractProcessor {

    private static final String INDENT = "    ";
    private static final String MODEL_IMPL_SUFFIX = "Model";
    private static final String INFO_IMPL_SUFFIX = "Info";
    private static final String REGISTRY_SERVICE_FILE = "META-INF/services/io.helidon.build.cli.harness.CommandRegistry";
    private static final List<String> ARGUMENT_VTYPES = Argument.VALUE_TYPES.stream().map(Class::getName).collect(Collectors.toList());
    private static final List<String> OPTION_VTYPES = Argument.VALUE_TYPES.stream().map(Class::getName).collect(Collectors.toList());

    private final Map<String, List<CommandMetaModel>> commandModelsByPkg = new HashMap<>();
    private final Map<String, ParametersMetaModel> fragmentsByQualifiedName = new HashMap<>();
    private final Set<String> rootTypes = new HashSet<>();
    private boolean done;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!done) {
            // store the qualified names of the root element to infer the compilation units and play nice
            // with the incremental compilation performed by IDEs.
            for (Element elt : roundEnv.getRootElements()) {
                if (elt instanceof TypeElement) {
                    rootTypes.add(((TypeElement) elt).getQualifiedName().toString());
                }
            }
            // process and cache the fragments
            for (Element elt : roundEnv.getElementsAnnotatedWith(CommandFragment.class)) {
                ParametersMetaModel metaModel = elt.accept(new ParametersVisitor(), null);
                fragmentsByQualifiedName.put(metaModel.typeElt.getQualifiedName().toString(), metaModel);
            }
            // process the command classes
            for (Element elt : roundEnv.getElementsAnnotatedWith(Command.class)) {
                CommandMetaModel metaModel = elt.accept(new CommandVisitor(), null);
                List<CommandMetaModel> metaModels = commandModelsByPkg.get(metaModel.pkg);
                if (metaModels == null) {
                    metaModels = new ArrayList<>();
                    commandModelsByPkg.put(metaModel.pkg, metaModels);
                }
                metaModels.add(metaModel);
            }
            try {
               generateSources();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            done = true;
        }
        return true;
    }

    private void generateSources() throws IOException {
        Filer filer = processingEnv.getFiler();

        // comment fragments
        for (ParametersMetaModel metaModel : fragmentsByQualifiedName.values()) {
            String fragmentSimpleName = metaModel.typeElt.getSimpleName().toString();
            String infoSimpleName = fragmentSimpleName + INFO_IMPL_SUFFIX;
            String infoQualifiedName = metaModel.pkg + "." + infoSimpleName;
            JavaFileObject fileObject = filer.createSourceFile(infoQualifiedName, metaModel.typeElt);
            generateCommandFragmentInfo(fileObject, metaModel.pkg, infoSimpleName, fragmentSimpleName, metaModel.params);
        }

        // commands
        for (Entry<String, List<CommandMetaModel>> entry : commandModelsByPkg.entrySet()) {
            List<CommandMetaModel> metaModels = entry.getValue();
            for (CommandMetaModel metaModel : metaModels) {
                String cmdSimpleName = metaModel.typeElt.getSimpleName().toString();
                String modelSimpleName = cmdSimpleName + MODEL_IMPL_SUFFIX;
                String modelQualifiedName = metaModel.pkg + "." + modelSimpleName;
                JavaFileObject fileObject = filer.createSourceFile(modelQualifiedName, metaModel.typeElt);
                generateCommandModel(fileObject, metaModel.pkg, modelSimpleName, metaModel.command, cmdSimpleName, metaModel.params);
            }
            String pkg = entry.getKey();
            String registrySimpleName = "CommandRegistryImpl";
            String registryQualifiedName = pkg + "." + registrySimpleName;
            JavaFileObject fileObject = filer.createSourceFile(registryQualifiedName);
            generateCommandRegistry(fileObject, pkg, registrySimpleName, metaModels);
            FileObject serviceFileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, "", REGISTRY_SERVICE_FILE);
            try (BufferedWriter bw = new BufferedWriter(serviceFileObject.openWriter())) {
                bw.append(registryQualifiedName).append("\n");
            }
        }
    }

    private void generateCommandFragmentInfo(JavaFileObject fileObject, String pkg, String infoName, String clazz,
            List<MetaModel> params) throws IOException {

        try (BufferedWriter bw = new BufferedWriter(fileObject.openWriter())) {
            bw.append("package ").append(pkg).append(";\n")
                    .append("\n")
                    .append("import ").append(CommandParameters.class.getName()).append(";\n")
                    .append("import ").append(CommandParser.class.getName()).append(";\n")
                    .append("import ").append(CommandModel.class.getName().replace("$", ".")).append(";\n")
                    .append("import ").append(Option.class.getName()).append(";\n")
                    .append("\n")
                    .append("final class ").append(infoName).append(" extends CommandParameters.CommandFragmentInfo<").append(clazz).append("> {\n")
                    .append("\n")
                    .append(INDENT).append("static final ").append(infoName).append(" INSTANCE = new ").append(infoName).append("();\n")
                    .append("\n")
                    .append(INDENT).append("private ").append(infoName).append("() {\n")
                    .append(INDENT).append(INDENT).append("super(").append(clazz).append(".class);\n")
                    .append(addParameter(params, INDENT + INDENT)).append("\n")
                    .append(INDENT).append("}\n")
                    .append("\n")
                    .append(INDENT).append("@Override\n")
                    .append(INDENT).append("public ").append(clazz).append(" create(CommandParser parser) {\n")
                    .append(INDENT).append(INDENT).append("return new ").append(clazz).append("(\n")
                    .append(resolveParams(params, INDENT + INDENT + INDENT)).append(");\n")
                    .append(INDENT).append("}\n")
                    .append("}\n");
        }
    }

    private void generateCommandRegistry(JavaFileObject fileObject, String pkg, String name, List<CommandMetaModel> metaModels)
            throws IOException {

        try (BufferedWriter bw = new BufferedWriter(fileObject.openWriter())) {
            bw.append("package ").append(pkg).append(";\n")
                    .append("\n")
                    .append("import ").append(CommandRegistry.class.getName()).append(";\n")
                    .append("\n")
                    .append("public final class ").append(name).append(" extends CommandRegistry {\n")
                    .append("\n")
                    .append(INDENT).append("public ").append(name).append("() {\n")
                    .append(INDENT).append(INDENT).append("super(\"").append(pkg).append("\");\n")
                    .append(registerModels(metaModels, INDENT + INDENT)).append("\n")
                    .append(INDENT).append("}\n")
                    .append("}\n");
        }
    }

    private void generateCommandModel(JavaFileObject fileObject, String pkg, String modelName, CommandInfo command,
            String clazz, List<MetaModel> params) throws IOException {

        try (BufferedWriter bw = new BufferedWriter(fileObject.openWriter())) {
            bw.append("package ").append(pkg).append(";\n")
                    .append("\n")
                    .append("import ").append(CommandExecution.class.getName()).append(";\n")
                    .append("import ").append(CommandModel.class.getName()).append(";\n")
                    .append("import ").append(CommandParser.class.getName()).append(";\n")
                    .append("import ").append(Option.class.getName()).append(";\n")
                    .append("\n")
                    .append("final class ").append(modelName).append(" extends CommandModel {\n")
                    .append("\n")
                    .append(INDENT).append(modelName).append("() {\n")
                    .append(INDENT).append(INDENT).append("super(").append(generateNewCommandInfo(command)).append(");\n")
                    .append(addParameter(params, INDENT + INDENT)).append("\n")
                    .append(INDENT).append("}\n")
                    .append("\n")
                    .append(INDENT).append("@Override\n")
                    .append(INDENT).append("public CommandExecution createExecution(CommandParser parser) {\n")
                    .append(INDENT).append(INDENT).append("return new ").append(clazz).append("(\n")
                    .append(resolveParams(params, INDENT + INDENT + INDENT)).append(");\n")
                    .append(INDENT).append("}\n")
                    .append("}\n");
        }
    }

    private String resolveParams(List<MetaModel> params, String indent) {
        StringBuilder sb = new StringBuilder();
        Iterator<MetaModel> it = params.iterator();
        for (int i=0 ; it.hasNext() ; i++) {
            MetaModel param = it.next();
            if (param == null) {
                throw new NullPointerException("model is null");
            }
            String paramTypeSimpleName = param.typeElt.getSimpleName().toString();
            String paramTypeQualifiedName = param.typeElt.getQualifiedName().toString();
            if (param instanceof ParametersMetaModel) {
                sb.append(indent)
                        .append(paramTypeSimpleName).append(INFO_IMPL_SUFFIX).append(".INSTANCE.getOrCreate(parser)");
            } else {
                sb.append(indent)
                        .append("parser.resolve(param(").append(paramTypeQualifiedName).append(".class, ")
                        .append(i).append("))");
            }
            if (it.hasNext()) {
                sb.append(",\n");
            }
        }
        return sb.toString();
    }

    private String registerModels(List<CommandMetaModel> commands, String indent) {
        StringBuilder sb = new StringBuilder();
        Iterator<CommandMetaModel> it = commands.iterator();
        while(it.hasNext()) {
            String modelSimpleName = it.next().typeElt.getSimpleName() + MODEL_IMPL_SUFFIX;
            sb.append(indent).append("register(new ").append(modelSimpleName).append("());");
            if (it.hasNext()) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String generateNewCommandInfo(CommandInfo command) throws IOException {
        return new StringBuilder("new CommandInfo(\"")
                .append(command.name())
                .append("\", \"")
                .append(command.description())
                .append("\")")
                .toString();
    }

    private String addParameter(List<MetaModel> params, String indent) {
        StringBuilder sb = new StringBuilder();
        Iterator<MetaModel> it = params.iterator();
        while(it.hasNext()) {
            MetaModel param = it.next();
            if (param == null) {
                throw new NullPointerException("Attribute is null");
            }
            sb.append(indent).append("addParameter(");
            String type = param.typeElt.getQualifiedName().toString();
            if (param instanceof OptionMetaModel) {
                Option option = ((OptionMetaModel) param).option;
                sb.append("new CommandModel.OptionInfo<>(")
                        .append(type).append(".class, \"")
                        .append(option.name()).append("\", \"")
                        .append(option.description()).append("\", ")
                        .append(option.required() ? "true" : "false")
                        .append(", Option.Scope.").append(option.scope().name())
                        .append(")");
            } else if (param instanceof ArgumentMetaModel) {
                Argument argument = ((ArgumentMetaModel) param).argument;
                sb.append("new CommandModel.ArgumentInfo<>(")
                        .append(type).append(".class, \"")
                        .append(argument.description()).append("\", ")
                        .append(argument.required() ? "true" : "false")
                        .append(")");
            } else {
                sb.append(param.typeElt.getSimpleName())
                        .append(INFO_IMPL_SUFFIX)
                        .append(".INSTANCE");
            }
            sb.append(");");
            if (it.hasNext()) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private final class ConstructorVisitor extends SimpleElementVisitor9<List<MetaModel>, Void> {

        private boolean processOption(VariableElement varElt, TypeElement typeElt, List<String> options, List<MetaModel> params) {
            Option optionAnnot = varElt.getAnnotation(Option.class);
            if (optionAnnot != null) {
                String optionName = optionAnnot.name();
                if (optionName == null) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "option name cannot be null", varElt);
                } else if (!Option.NAME_PREDICATE.test(optionName)) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            String.format("'%s' is not a valid option name", optionName),
                            varElt);
                } else if (options.contains(optionName)) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            String.format("option named '%s' is already defined", optionName),
                            varElt);
                } else if (optionAnnot.description() == null) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "description cannot be null", varElt);
                } else {
                    String typeQualifiedName = typeElt.getQualifiedName().toString();
                    if (!OPTION_VTYPES.contains(typeQualifiedName)) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                String.format("%s is not a valid option value type: " + typeQualifiedName),
                                varElt);
                    } else {
                        params.add(new OptionMetaModel(typeElt, optionAnnot));
                        options.add(optionName);
                    }
                }
                return true;
            }
            return false;
        }

        private boolean processArgument(VariableElement varElt, TypeElement typeElt, List<MetaModel> params) {
            Argument argumentAnnot = varElt.getAnnotation(Argument.class);
            if (argumentAnnot != null) {
                if (argumentAnnot.description() == null) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "description cannot be null", varElt);
                } else {
                    String typeQualifiedName = typeElt.getQualifiedName().toString();
                    if (!ARGUMENT_VTYPES.contains(typeQualifiedName)) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                String.format("%s is not a valid argument value type: " + typeQualifiedName),
                                varElt);
                    } else {
                        params.add(new ArgumentMetaModel(typeElt, argumentAnnot));
                    }
                }
                return true;
            }
            return false;
        }

        private void processFragment(VariableElement varElt, TypeElement typeElt, List<String> options, List<MetaModel> params) {
            String fragmentQualifiedName = typeElt.getQualifiedName().toString();
            ParametersMetaModel fragmentModel = fragmentsByQualifiedName.get(fragmentQualifiedName);
            if (fragmentModel == null) {
                if (rootTypes.contains(fragmentQualifiedName)) {
                    // report an error if the fragment related file is present in the compilation unit
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            String.format("type '%s' is not annotated with @%s", typeElt,
                                    CommandFragment.class.getSimpleName()),
                            varElt);
                }
            } else {
                List<String> optionDuplicates = fragmentModel.optionDuplicates(options);
                if (!optionDuplicates.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    Iterator<String> it = optionDuplicates.iterator();
                    while (it.hasNext()) {
                        sb.append(it.next());
                        if (it.hasNext()) {
                            sb.append(", ");
                        }
                    }
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            String.format("command fragment duplicates options: '%s'", sb),
                            varElt);
                }
                options.addAll(fragmentModel.optionNames());
                params.add(fragmentModel);
            }
        }

        @Override
        public List<MetaModel> visitExecutable(ExecutableElement elt, Void p) {
            Types types = processingEnv.getTypeUtils();
            List<MetaModel> params = new LinkedList<>();
            List<String> optionNames = new ArrayList<>();
            for (VariableElement varElt : elt.getParameters()) {
                String varName = varElt.getSimpleName().toString();
                // resolve the type
                TypeMirror varType = varElt.asType();
                TypeKind varTypeKind = varType.getKind();
                boolean primitive = varTypeKind.isPrimitive();
                TypeElement typeElt;
                if (primitive) {
                    typeElt = types.boxedClass(types.getPrimitiveType(varTypeKind));
                } else {
                    typeElt = (TypeElement) types.asElement(varElt.asType());
                }
                if (typeElt == null) {
                    throw new IllegalStateException("Unable to resolve type for variable: " + varName);
                }

                // process the variable
                if (!processOption(varElt, typeElt, optionNames, params)
                        && !processArgument(varElt, typeElt, params)) {

                    if (!primitive) {
                        processFragment(varElt, typeElt, optionNames, params);
                    } else {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                String.format("%s is not a valid attribute", varName),
                                varElt);
                    }
                }
            }
            return params;
        }
    }

    private final class CommandVisitor extends SimpleElementVisitor9<CommandMetaModel, Void> {

        @Override
        public CommandMetaModel visitType(TypeElement typeElt, Void p) {
            Command annot = typeElt.getAnnotation(Command.class);
            CommandInfo command = new CommandInfo(annot.name(), annot.description());
            ParametersMetaModel optionsModel = typeElt.accept(new ParametersVisitor(), null);
            return new CommandMetaModel(optionsModel, command);
        }
    }

    private final class ParametersVisitor extends SimpleElementVisitor9<ParametersMetaModel, Void> {

        @Override
        public ParametersMetaModel visitType(TypeElement typeElt, Void p) {
            List<MetaModel> options = null;
            for (Element elt : typeElt.getEnclosedElements()) {
                if (elt.getKind() == ElementKind.CONSTRUCTOR && elt.getAnnotation(Creator.class) != null) {
                    options = elt.accept(new ConstructorVisitor(), null);
                }
            }
            if (options == null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        String.format("No constructor annotated with @%s found", Creator.class.getSimpleName()),
                        typeElt);
            }
            return new ParametersMetaModel(typeElt, options);
        }
    }

    private abstract class MetaModel {

        protected final TypeElement typeElt;
        protected final String pkg;

        MetaModel(TypeElement typeElt) {
            this.typeElt = Objects.requireNonNull(typeElt, "typeElt is null");
            this.pkg = processingEnv.getElementUtils().getPackageOf(typeElt).getQualifiedName().toString();
        }
    }

    private class ArgumentMetaModel extends MetaModel {

        private final Argument argument;

        ArgumentMetaModel(TypeElement typeElt, Argument argument) {
            super(typeElt);
            this.argument = Objects.requireNonNull(argument, "argument is null");
        }
    }

    private class OptionMetaModel extends MetaModel {

        private final Option option;

        OptionMetaModel(TypeElement typeElt, Option option) {
            super(typeElt);
            this.option = Objects.requireNonNull(option, "option is null");
        }
    }

    private class ParametersMetaModel extends MetaModel {

        protected final List<MetaModel> params;

        ParametersMetaModel(TypeElement typeElt, List<MetaModel> params) {
            super(typeElt);
            this.params = params;
        }

        ParametersMetaModel(ParametersMetaModel copy) {
            this(copy.typeElt, copy.params);
        }

        List<String> optionNames() {
            List<String> names = new ArrayList<>();
            for (MetaModel attr : params) {
                if (attr instanceof OptionMetaModel) {
                    names.add(((OptionMetaModel) attr).option.name());
                }
            }
            return names;
        }

        List<String> optionDuplicates(List<String> optionNames) {
            List<String> duplicates = new ArrayList<>();
            for (String optionName : optionNames()) {
                if (optionNames.contains(optionName)) {
                    duplicates.add(optionName);
                }
            }
            return duplicates;
        }
    }

    private final class CommandMetaModel extends ParametersMetaModel {

        private final CommandInfo command;

        CommandMetaModel(ParametersMetaModel optionsModel, CommandInfo command) {
            super(optionsModel);
            this.command = Objects.requireNonNull(command, "command is null");
        }
    }
}

package io.helidon.build.cli.codegen;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor9;
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
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.Iterator;
import java.util.stream.Collectors;

import io.helidon.build.cli.harness.CommandRegistry;
import io.helidon.build.cli.harness.Command;
import io.helidon.build.cli.harness.CommandExecution;
import io.helidon.build.cli.harness.CommandModel.CommandInfo;
import io.helidon.build.cli.harness.CommandFragment;
import io.helidon.build.cli.harness.CommandModel;
import io.helidon.build.cli.harness.CommandParameters;
import io.helidon.build.cli.harness.CommandParser;
import io.helidon.build.cli.harness.Creator;
import io.helidon.build.cli.harness.Option;
import io.helidon.build.cli.harness.Option.Argument;
import io.helidon.build.cli.harness.Option.Flag;
import io.helidon.build.cli.harness.Option.KeyValue;
import io.helidon.build.cli.harness.Option.KeyValues;
import java.io.File;

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
    private static final List<String> ARGUMENT_TYPES = Argument.SUPPORTED_TYPES.stream().map(Class::getName).collect(Collectors.toList());
    private static final List<String> KEY_VALUE_TYPES = KeyValue.SUPPORTED_TYPES.stream().map(Class::getName).collect(Collectors.toList());
    private static final List<String> KEY_VALUES_TYPES = KeyValues.SUPPORTED_TYPES.stream().map(Class::getName).collect(Collectors.toList());

    private final Map<String, List<CommandMetaModel>> commandsByPkg = new HashMap<>();
    private final Map<String, ParametersMetaModel> fragmentsByQualifiedName = new HashMap<>();
    private final Set<String> rootTypes = new HashSet<>();
    private boolean done;

    // TODO enforce options only for command fragments.
    // TODO support inheritance
    // TODO split the code in this class
    // TODO add unit tests
    // i.e no argument

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
                ParametersMetaModel fragment = elt.accept(new ParametersVisitor(), null);
                if (fragment != null) {
                    fragmentsByQualifiedName.put(fragment.typeElt.getQualifiedName().toString(), fragment);
                }
            }
            // process the command classes
            for (Element elt : roundEnv.getElementsAnnotatedWith(Command.class)) {
                CommandMetaModel command = elt.accept(new CommandVisitor(), null);
                if (command != null) {
                    List<CommandMetaModel> commands = commandsByPkg.get(command.pkg);
                    if (commands == null) {
                        commands = new ArrayList<>();
                        commandsByPkg.put(command.pkg, commands);
                    }
                    commands.add(command);
                }
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
        for (Entry<String, List<CommandMetaModel>> entry : commandsByPkg.entrySet()) {
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
            String s = "package " + pkg + ";\n"
                    + "\n"
                    + "import " + CommandModel.class.getName() + ";\n"
                    + "import " + CommandParameters.class.getName() + ";\n"
                    + "import " + CommandParser.class.getName() + ";\n"
                    + "\n"
                    + "final class " + infoName + " extends CommandParameters.CommandFragmentInfo<" + clazz + "> {\n"
                    + "\n"
                    + declareParameters(params, INDENT)
                    + INDENT + "static final " + infoName + " INSTANCE = new " + infoName + "();\n"
                    + "\n"
                    + INDENT + "private " + infoName + "() {\n"
                    + INDENT + INDENT + "super(" + clazz + ".class);\n"
                    + addParameter(params.size(), INDENT + INDENT) + "\n"
                    + INDENT + "}\n"
                    + "\n"
                    + INDENT + "@Override\n"
                    + INDENT + "public " + clazz + " resolve(CommandParser parser) {\n"
                    + INDENT + INDENT + "return new " + clazz + "(\n"
                    + resolveParams(params, INDENT + INDENT + INDENT) + ");\n"
                    + INDENT + "}\n"
                    + "}\n";
            bw.append(s);
        }
    }

    private void generateCommandRegistry(JavaFileObject fileObject, String pkg, String name, List<CommandMetaModel> metaModels)
            throws IOException {

        try (BufferedWriter bw = new BufferedWriter(fileObject.openWriter())) {
            String s = "package " + pkg + ";\n"
                    + "\n"
                    + "import " + CommandRegistry.class.getName() + ";\n"
                    + "\n"
                    + "public final class CommandRegistryImpl extends CommandRegistry {\n"
                    + "\n"
                    + INDENT + "public " + name + "() {\n"
                    + INDENT + INDENT + "super(\"" + pkg + "\");\n"
                    + registerModels(metaModels, INDENT + INDENT) + "\n"
                    + INDENT + "}\n"
                    + "}\n";
            bw.append(s);
        }
    }

    private void generateCommandModel(JavaFileObject fileObject, String pkg, String modelName, CommandInfo command,
            String clazz, List<MetaModel> params) throws IOException {

        try (BufferedWriter bw = new BufferedWriter(fileObject.openWriter())) {
            String s = "package " + pkg + ";\n"
                    + "\n"
                    + "import " + CommandExecution.class.getName() + ";\n"
                    + "import " + CommandModel.class.getName() + ";\n"
                    + "import " + CommandParameters.class.getName() + ";\n"
                    + "import " + CommandParser.class.getName() + ";\n"
                    + "\n"
                    + "final class " + modelName + " extends CommandModel {\n"
                    + "\n"
                    + declareParameters(params, INDENT)
                    + "\n"
                    + INDENT + modelName + "() {\n"
                    + INDENT + INDENT + "super(" + "new CommandInfo(\"" + command.name() + "\", \"" + command.description() + "\"));\n"
                    + addParameter(params.size(), INDENT + INDENT) + "\n"
                    + INDENT + "}\n"
                    + "\n"
                    + INDENT + "@Override\n"
                    + INDENT + "public CommandExecution createExecution(CommandParser parser) {\n"
                    + INDENT + INDENT + "return new " + clazz + "(\n"
                    + resolveParams(params, INDENT + INDENT + INDENT) + ");\n"
                    + INDENT + "}\n"
                    + "}\n";
            bw.append(s);
        }
    }

    private String resolveParams(List<MetaModel> params, String indent) {
        String s = "";
        Iterator<MetaModel> it = params.iterator();
        for (int i=1 ; it.hasNext() ; i++) {
            MetaModel param = it.next();
            if (param instanceof ParametersMetaModel) {
                s += indent + "OPTION" + i + ".resolve(parser)";
            } else {
                s += indent + "parser.resolve(OPTION" + i + ")";
            }
            if (it.hasNext()) {
                s += ",\n";
            }
        }
        return s;
    }

    private String registerModels(List<CommandMetaModel> commands, String indent) {
        String s = "";
        Iterator<CommandMetaModel> it = commands.iterator();
        while(it.hasNext()) {
            String modelSimpleName = it.next().typeElt.getSimpleName() + MODEL_IMPL_SUFFIX;
            s += indent + "register(new " + modelSimpleName + "());";
            if (it.hasNext()) {
                s += "\n";
            }
        }
        return s;
    }

    private String declareParameters(List<MetaModel> params, String indent) {
        String s = "";
        Iterator<MetaModel> it = params.iterator();
        for(int i=1 ; it.hasNext() ; i++) {
            MetaModel param = it.next();
            if (param == null) {
                continue;
            }
            s += indent + "static final ";
            if (param instanceof KeyValuesMetaModel) {
                String paramType = ((KeyValuesMetaModel) param).paramTypeElt.getQualifiedName().toString();
                KeyValues option = ((KeyValuesMetaModel) param).option;
                s += "CommandModel.KeyValuesInfo<" + paramType + "> OPTION" + i + " = new CommandModel.KeyValuesInfo<>("
                        + paramType + ".class, \"" + option.name() + "\", \"" + option.description() + "\", "
                        + String.valueOf(option.required()) + ");";
            } else if (param instanceof FlagMetaModel ){
                Flag option = ((FlagMetaModel) param).option;
                s += "CommandModel.FlagInfo OPTION" + i + " = new CommandModel.FlagInfo(\""
                        + option.name() + "\", \"" + option.description() + "\");";
            } else if (param.typeElt != null) {
                String type = param.typeElt.getQualifiedName().toString();
                if (param instanceof KeyValueMetaModel) {
                    KeyValue option = ((KeyValueMetaModel) param).option;
                    s += "CommandModel.KeyValueInfo<" + type + "> OPTION" + i + " = new CommandModel.KeyValueInfo<>("
                            + type + ".class, \"" + option.name() + "\", \"" + option.description()
                            + "\", " + defaultValue(type, option.defaultValue()) + ");";
                    option.defaultValue();
                } else if (param instanceof ArgumentMetaModel) {
                    Argument option = ((ArgumentMetaModel) param).option;
                    s += "CommandModel.ArgumentInfo<" + type + "> OPTION" + i + " = new CommandModel.ArgumentInfo<>("
                            + type + ".class, \"" + option.description() + "\", "
                            + String.valueOf(option.required()) + ");";
                } else {
                    s += "CommandParameters.CommandFragmentInfo<" + param.typeElt.getSimpleName() + "> OPTION" + i + " = "
                            + param.typeElt.getSimpleName() + INFO_IMPL_SUFFIX + ".INSTANCE;";
                }
            }
            s += "\n";
        }
        return s;
    }

    private String addParameter(int numParams, String indent) {
        String s = "";
        for(int i=1 ; i <= numParams; i++) {
            s += indent + "addParameter(OPTION" + i + ");";
            if (i < numParams) {
                s += "\n";
            }
        }
        return s;
    }

    private String defaultValue(String type, String value) {
        if (value == null || value.isEmpty()) {
            return "null";
        }
        // String.class, Integer.class, File.class
        if (String.class.getName().equals(type)) {
            return "\"" + value + "\"";
        }
        if (Integer.class.getName().equals(type)) {
            return "Integer.parseInt(\"" + value + "\")";
        }
        if (File.class.getName().equals(type)) {
            return "new java.io.File(\"" + value + "\")";
        }
        throw new IllegalArgumentException("Unsupported type: " + type);
    }

    private boolean checkOptionName(String name, VariableElement varElt, List<String> options) {
        if (name.isEmpty()) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "option name cannot be empty", varElt);
            return false;
        }
        if (!Option.NAME_PREDICATE.test(name)) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    String.format("'%s' is not a valid option name", name),
                    varElt);
            return false;
        }
        if (options.contains(name)) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    String.format("option named '%s' is already defined", name),
                    varElt);
            return false;
        }
        return true;
    }

    private boolean checkOptionDescription(String desc, VariableElement varElt) {
        if (desc.isEmpty()) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "description cannot be empty", varElt);
            return false;
        }
        return true;
    }

    private boolean processOption(VariableElement varElt, TypeElement typeElt, List<String> options, List<MetaModel> params) {
        String typeQualifiedName = typeElt.getQualifiedName().toString();
        Argument argument = varElt.getAnnotation(Argument.class);
        if (argument != null) {
            if (checkOptionDescription(argument.description(), varElt)) {
                if (ARGUMENT_TYPES.contains(typeQualifiedName)) {
                    params.add(new ArgumentMetaModel(typeElt, argument));
                    return true;
                } else {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            String.format("%s is not a valid argument type: ", typeQualifiedName),
                            varElt);
                }
            }
            return false;
        }
        Flag flag = varElt.getAnnotation(Flag.class);
        if (flag != null) {
            if (checkOptionName(flag.name(), varElt, options) && checkOptionDescription(flag.description(), varElt)) {
                if (Boolean.class.getName().equals(typeQualifiedName)) {
                    params.add(new FlagMetaModel(flag));
                    options.add(flag.name());
                    return true;
                } else {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            String.format("%s is not a valid flag type: ", typeQualifiedName),
                            varElt);
                }
            }
            return false;
        }
        KeyValue keyValue = varElt.getAnnotation(KeyValue.class);
        if (keyValue != null) {
            if (checkOptionName(keyValue.name(), varElt, options) && checkOptionDescription(keyValue.description(), varElt)) {
                if (KEY_VALUE_TYPES.contains(typeQualifiedName)
                        || ElementKind.ENUM.equals(typeElt.getKind())) {
                    params.add(new KeyValueMetaModel(typeElt, keyValue));
                    options.add(keyValue.name());
                    return true;
                } else {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            String.format("%s is not a valid key-value type: ", typeElt),
                            varElt);
                }
            }
            return false;
        }
        KeyValues keyValues = varElt.getAnnotation(KeyValues.class);
        if (keyValues != null) {
            if (checkOptionName(keyValues.name(), varElt, options)
                    && checkOptionDescription(keyValues.description(), varElt)) {
                if (Collection.class.getName().equals(typeQualifiedName)) {
                    TypeElement paramTypeElt = varElt.asType().accept(new TypeParamVisitor(), null);
                    if (paramTypeElt != null) {
                        String paramTypeQualifiedName = paramTypeElt.getQualifiedName().toString();
                        if (KEY_VALUES_TYPES.contains(paramTypeQualifiedName)) {
                            params.add(new KeyValuesMetaModel(paramTypeElt, keyValues));
                            options.add(keyValues.name());
                            return true;
                        } else {
                            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                    String.format("%s is not a valid option values type parameter: ", paramTypeQualifiedName),
                                    varElt);
                        }
                    }
                } else {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            String.format("%s is not a valid key-values type: ", typeElt),
                            varElt);
                }
            }
            return false;
        }
        return false;
    }

    private final class TypeParamVisitor extends SimpleTypeVisitor9<TypeElement, Void> {

        @Override
        public TypeElement visitDeclared(DeclaredType t, Void p) {
            List<? extends TypeMirror> typeArguments = t.getTypeArguments();
            if (typeArguments.size() == 1) {
                TypeElement typeElt = (TypeElement) processingEnv.getTypeUtils().asElement(typeArguments.get(0));
                return typeElt;
            }
            return null;
        }
    }

    private boolean processArgument(VariableElement varElt, TypeElement typeElt, List<MetaModel> params) {
        Argument argumentAnnot = varElt.getAnnotation(Argument.class);
        if (argumentAnnot != null) {
            if (argumentAnnot.description() == null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "description cannot be null", varElt);
            } else {
                String typeQualifiedName = typeElt.getQualifiedName().toString();
                if (!ARGUMENT_TYPES.contains(typeQualifiedName)) {
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

    private final class ConstructorVisitor extends SimpleElementVisitor9<List<MetaModel>, Void> {

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
                        return null;
                    }
                }
            }
            return params;
        }
    }

    private boolean implementsCommandExecution(TypeElement typeElt) {
        for (TypeMirror iface : typeElt.getInterfaces()) {
            TypeElement ifaceTypeElt = (TypeElement) processingEnv.getTypeUtils().asElement(iface);
            if (CommandExecution.class.getName().equals(ifaceTypeElt.getQualifiedName().toString())) {
                return true;
            }
        }
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                String.format("%s does not implement %s", typeElt, CommandExecution.class.getSimpleName()),
                typeElt);
        return false;
    }

    private final class CommandVisitor extends SimpleElementVisitor9<CommandMetaModel, Void> {

        @Override
        public CommandMetaModel visitType(TypeElement typeElt, Void p) {
            if (implementsCommandExecution(typeElt)) {
                Command annot = typeElt.getAnnotation(Command.class);
                CommandInfo command = new CommandInfo(annot.name(), annot.description());
                ParametersMetaModel params = typeElt.accept(new ParametersVisitor(), null);
                if (params != null) {
                    return new CommandMetaModel(params, command);
                }
            }
            return null;
        }
    }

    private final class ParametersVisitor extends SimpleElementVisitor9<ParametersMetaModel, Void> {

        @Override
        public ParametersMetaModel visitType(TypeElement typeElt, Void p) {
            List<MetaModel> params = null;
            for (Element elt : typeElt.getEnclosedElements()) {
                if (elt.getKind() == ElementKind.CONSTRUCTOR && elt.getAnnotation(Creator.class) != null) {
                    params = elt.accept(new ConstructorVisitor(), null);
                    break;
                }
            }
            if (params != null) {
                return new ParametersMetaModel(typeElt, params);
            }
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        String.format("No constructor annotated with @%s found", Creator.class.getSimpleName()),
                        typeElt);
            return null;
        }
    }

    private abstract class MetaModel {

        protected final TypeElement typeElt;

        MetaModel() {
            this.typeElt = null;
        }

        MetaModel(TypeElement typeElt) {
            this.typeElt = typeElt;
        }
    }

    private interface DescribedOptionMetaModel {
        String description();
    }

    private interface NamedOptionMetaModel extends DescribedOptionMetaModel {
        String name();
    }

    private class ArgumentMetaModel extends MetaModel implements DescribedOptionMetaModel {

        private final Argument option;

        ArgumentMetaModel(TypeElement typeElt, Argument option) {
            super(typeElt);
            this.option = Objects.requireNonNull(option, "option is null");
        }

        @Override
        public String description() {
            return option.description();
        }
    }

    private class FlagMetaModel extends MetaModel implements NamedOptionMetaModel {

        private final Flag option;

        FlagMetaModel(Flag option) {
            super();
            this.option = Objects.requireNonNull(option, "option is null");
        }

        @Override
        public String description() {
            return option.description();
        }

        @Override
        public String name() {
            return option.name();
        }
    }

    private class KeyValueMetaModel extends MetaModel implements NamedOptionMetaModel {

        private final KeyValue option;

        KeyValueMetaModel(TypeElement typeElt, KeyValue  option) {
            super(typeElt);
            this.option = Objects.requireNonNull(option, "option is null");
        }

        @Override
        public String name() {
            return option.name();
        }

        @Override
        public String description() {
            return option.description();
        }
    }

    private class KeyValuesMetaModel extends MetaModel implements NamedOptionMetaModel {

        private final KeyValues option;
        private final TypeElement paramTypeElt;

        KeyValuesMetaModel(TypeElement paramTypeElt, KeyValues option) {
            super();
            this.option = Objects.requireNonNull(option, "option is null");
            this.paramTypeElt = Objects.requireNonNull(paramTypeElt, "paramTypeElt is null");
        }

        @Override
        public String description() {
            return option.description();
        }

        @Override
        public String name() {
            return option.name();
        }
    }

    private class ParametersMetaModel extends MetaModel {

        protected final List<MetaModel> params;
        protected final String pkg;

        ParametersMetaModel(TypeElement typeElt, List<MetaModel> params) {
            super(Objects.requireNonNull(typeElt, "typeElt is null"));
            this.pkg = processingEnv.getElementUtils().getPackageOf(typeElt).getQualifiedName().toString();
            this.params = params;
        }

        ParametersMetaModel(ParametersMetaModel copy) {
            this(copy.typeElt, copy.params);
        }

        List<String> optionNames() {
            List<String> names = new ArrayList<>();
            for (MetaModel attr : params) {
                if (attr instanceof NamedOptionMetaModel) {
                    names.add(((NamedOptionMetaModel) attr).name());
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

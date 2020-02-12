package io.helidon.build.cli.codegen;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.SimpleElementVisitor9;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.function.Predicate;
import java.util.Iterator;

import io.helidon.build.cli.harness.Command;
import io.helidon.build.cli.harness.CommandExecution;
import io.helidon.build.cli.harness.CommandModel.CommandInfo;
import io.helidon.build.cli.harness.CommandModel.OptionInfo;
import io.helidon.build.cli.harness.Option;
import io.helidon.build.cli.harness.OptionName;
import io.helidon.build.cli.harness.CommandFragment;
import io.helidon.build.cli.harness.CommandModel;
import io.helidon.build.cli.harness.CommandParser;
import io.helidon.build.cli.harness.CommandRegistry;
import java.util.HashSet;

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
    private static final String REGISTRY_SERVICE_FILE = "META-INF/services/io.helidon.build.cli.harness.CommandRegistry";

    private final Map<String, List<CommandMetaModel>> commandModelsByPkg = new HashMap<>();
    private final Map<String, OptionsMetaModel> fragmentsByQualifiedName = new HashMap<>();
    private final Set<String> rootTypes = new HashSet<>();
    private boolean done;

    // TODO option duplicates

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
                OptionsMetaModel metaModel = elt.accept(new OptionsVisitor(), null);
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
        for (Entry<String, List<CommandMetaModel>> entry : commandModelsByPkg.entrySet()) {
            List<CommandMetaModel> metaModels = entry.getValue();
            for (CommandMetaModel metaModel : metaModels) {
                String modelSimpleName = metaModel.typeElt.getSimpleName() + MODEL_IMPL_SUFFIX;
                String modelQualifiedName = metaModel.pkg + "." + modelSimpleName;
                JavaFileObject fileObject = filer.createSourceFile(modelQualifiedName, metaModel.typeElt);
                generateCommandModel(metaModel.pkg, modelSimpleName, fileObject, metaModel.command, metaModel.options.values());
            }
            String pkg = entry.getKey();
            String registrySimpleName = "CommandRegistryImpl";
            String registryQualifiedName = pkg + "." + registrySimpleName;
            JavaFileObject fileObject = filer.createSourceFile(registryQualifiedName);
            generateCommandRegistry(pkg, registrySimpleName, fileObject, metaModels);
            FileObject serviceFileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, "", REGISTRY_SERVICE_FILE);
            try (BufferedWriter bw = new BufferedWriter(serviceFileObject.openWriter())) {
                bw.append(registryQualifiedName).append("\n");
            }
        }

        // TODO generate createExecution method using CommandParser
    }

    private void generateCommandRegistry(String pkg, String name, JavaFileObject fileObject,
            List<CommandMetaModel> metaModels) throws IOException {

        try (BufferedWriter bw = new BufferedWriter(fileObject.openWriter())) {
            bw.append("package ").append(pkg).append(" ;\n")
                    .append("\n")
                    .append("import java.util.Optional;\n")
                    .append("import java.util.HashMap;\n")
                    .append("import java.util.Map;\n")
                    .append("\n")
                    .append("import ").append(CommandModel.class.getName()).append(";\n")
                    .append("import ").append(CommandRegistry.class.getName()).append(";\n")
                    .append("\n")
                    .append("public final class ").append(name).append(" implements CommandRegistry {\n")
                    .append("\n")
                    .append(INDENT).append("final Map<String, CommandModel> commandModels;\n")
                    .append("\n")
                    .append(INDENT).append("public ").append(name).append("() {\n")
                    .append(INDENT).append(INDENT).append(generateNewCommandModelsMap(metaModels, INDENT + INDENT)).append("\n")
                    .append(INDENT).append("}\n")
                    .append("\n")
                    .append(INDENT).append("@Override\n")
                    .append(INDENT).append("public String pkg() {\n")
                    .append(INDENT).append(INDENT).append("return \"").append(pkg).append("\";\n")
                    .append(INDENT).append("}\n")
                    .append("\n")
                    .append(INDENT).append("@Override\n")
                    .append(INDENT).append("public Optional<CommandModel> get(String name) {\n")
                    .append(INDENT).append(INDENT).append("return Optional.ofNullable(commandModels.get(name));\n")
                    .append(INDENT).append("}\n")
                    .append("}\n");
        }
    }

    private void generateCommandModel(String pkg, String name, JavaFileObject fileObject, CommandInfo command,
            Collection<OptionMetaModel> options) throws IOException {

        try (BufferedWriter bw = new BufferedWriter(fileObject.openWriter())) {
            bw.append("package ").append(pkg).append(" ;\n")
                    .append("\n")
                    .append("import java.util.HashMap;\n")
                    .append("import java.util.Map;\n")
                    .append("\n")
                    .append("import ").append(CommandExecution.class.getName()).append(";\n")
                    .append("import ").append(CommandModel.class.getName()).append(";\n")
                    .append("import ").append(CommandParser.class.getName()).append(";\n")
                    .append("\n")
                    .append("final class ").append(name).append(" implements CommandModel {\n")
                    .append("\n")
                    .append(INDENT).append("final CommandInfo commandInfo;\n")
                    .append(INDENT).append("final Map<String, OptionInfo> options;\n")
                    .append("\n")
                    .append(INDENT).append(name).append("() {\n")
                    .append(INDENT).append(INDENT).append(generateNewCommandInfo(command)).append("\n")
                    .append(INDENT).append(INDENT).append(generateNewOptionsMap(options, INDENT + INDENT)).append("\n")
                    .append(INDENT).append("}\n")
                    .append("\n")
                    .append(INDENT).append("@Override\n")
                    .append(INDENT).append("public CommandInfo command() {\n")
                    .append(INDENT).append(INDENT).append("return commandInfo;\n")
                    .append(INDENT).append("}\n")
                    .append("\n")
                    .append(INDENT).append("@Override\n")
                    .append(INDENT).append("public Map<String, OptionInfo> options() {\n")
                    .append(INDENT).append(INDENT).append("return options;\n")
                    .append(INDENT).append("}\n")
                    .append("\n")
                    .append(INDENT).append("@Override\n")
                    .append(INDENT).append("public CommandExecution createExecution(CommandParser parser) {\n")
                    .append(INDENT).append(INDENT).append("throw new UnsupportedOperationException();\n")
                    .append(INDENT).append("}\n")
                    .append("}\n");
        }
    }

    private String generateNewCommandModelsMap(List<CommandMetaModel> metaModels, String indent) {
        StringBuilder sb = new StringBuilder("commandModels = new HashMap<>();\n");
        for (CommandMetaModel metaModel : metaModels) {
            String modelSimpleName = metaModel.typeElt.getSimpleName() + MODEL_IMPL_SUFFIX;
            sb.append(indent)
                    .append("commandModels.put(\"")
                    .append(metaModel.command.name())
                    .append("\", new ")
                    .append(modelSimpleName)
                    .append("());\n");
        }
        return sb.toString();
    }

    private String generateNewCommandInfo(CommandInfo command) throws IOException {
        return new StringBuilder("commandInfo = new CommandInfo(\"")
                .append(command.name())
                .append("\", \"")
                .append(command.description())
                .append("\");")
                .toString();
    }

    private String generateNewOptionsMap(Collection<OptionMetaModel> options, String indent) {
        StringBuilder sb = new StringBuilder("options = new HashMap<>();\n");
        for (OptionMetaModel optionModel : options) {
            OptionInfo option = optionModel.option;
            sb.append(indent).append("options.put(\"").append(option.name()).append("\", new OptionInfo(\"")
                    .append(option.name()).append("\", \"")
                    .append(option.description()).append("\", ")
                    .append(option.required() ? "true" : "false")
                    .append("));\n");
        }
        return sb.toString();
    }

    private final class ConstructorVisitor extends SimpleElementVisitor9<LinkedList<TypeMetaModel>, Map<String, OptionMetaModel>> {

        @Override
        public LinkedList<TypeMetaModel> visitExecutable(ExecutableElement elt, Map<String, OptionMetaModel> optionModels) {
            Messager messager = processingEnv.getMessager();
            LinkedList<TypeMetaModel> parameters = new LinkedList<>();
            for (VariableElement varElt : elt.getParameters()) {
                OptionName optionNameAnnot = varElt.getAnnotation(OptionName.class);
                if (optionNameAnnot != null) {
                    String optionName = optionNameAnnot.value();
                    OptionMetaModel optionModel = optionModels.get(optionName);
                    if (optionModel == null) {
                        messager.printMessage(Diagnostic.Kind.ERROR,
                                String.format("option named '%s' cannot be found", optionName),
                                varElt);
                    } else {
                        parameters.add(optionModel);
                    }
                    continue;
                }
                String varName = varElt.getSimpleName().toString();
                OptionMetaModel optionModel = optionModels.get(varName);
                if (optionModel == null) {
                    TypeElement typeElt = (TypeElement) processingEnv.getTypeUtils().asElement(varElt.asType());
                    if (typeElt == null) {
                        messager.printMessage(Diagnostic.Kind.ERROR,
                                String.format("parameter '%s' does not correspond to a field annotated with @%s",
                                        varName, Option.class.getSimpleName()),
                                varElt);
                        continue;
                    }
                    String fragmentQualifiedName = typeElt.getQualifiedName().toString();
                    OptionsMetaModel optionsModel = fragmentsByQualifiedName.get(fragmentQualifiedName);
                    if (optionsModel == null) {
                        if (rootTypes.contains(fragmentQualifiedName)) {
                            messager.printMessage(Diagnostic.Kind.ERROR,
                                    String.format("type '%s' is not annotated with @%s", typeElt,
                                            CommandFragment.class.getSimpleName()),
                                    varElt);
                        }
                    } else {
                        parameters.add(optionsModel);
                    }
                } else {
                    parameters.add(optionModel);
                }
            }
            return parameters;
        }
    }

    private final class CommandVisitor extends SimpleElementVisitor9<CommandMetaModel, Void> {

        @Override
        public CommandMetaModel visitType(TypeElement typeElt, Void p) {
            Command annot = typeElt.getAnnotation(Command.class);
            CommandInfo command = new CommandInfo(annot.name(), annot.description());
            OptionsMetaModel optionsModel = typeElt.accept(new OptionsVisitor(), null);
            return new CommandMetaModel(optionsModel, command);
        }
    }

    private final class OptionsVisitor extends SimpleElementVisitor9<OptionsMetaModel, Void> {

        @Override
        public OptionsMetaModel visitType(TypeElement typeElt, Void p) {
            Map<String, OptionMetaModel> optionModels = new HashMap<>();
            for (Element elt : typeElt.getEnclosedElements()) {
                if (elt.getKind() == ElementKind.FIELD) {
                    Option annot = elt.getAnnotation(Option.class);
                    if (annot != null) {
                        String optionName = annot.name();
                        if (!Option.NAME_PREDICATE.test(optionName)) {
                            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                    String.format("'%s' is not a valid option name", optionName),
                                    elt);
                        }
                        OptionInfo optionInfo = new OptionInfo(optionName, annot.description(), annot.required());
                        Element eltType = processingEnv.getTypeUtils().asElement(elt.asType());
                        optionModels.put(optionName, new OptionMetaModel((TypeElement) eltType, optionInfo));
                    }
                }
            }
            LinkedList<TypeMetaModel> constructorArgs = null;
            for (Element elt : typeElt.getEnclosedElements()) {
                if (elt.getKind() == ElementKind.CONSTRUCTOR) {
                    if (constructorArgs == null) {
                        constructorArgs = elt.accept(new ConstructorVisitor(), optionModels);
                    }
                }
            }
            if (!optionModels.isEmpty() && (constructorArgs == null || constructorArgs.isEmpty())) {
                StringBuilder sb = new StringBuilder();
                Iterator<String> it = optionModels.keySet().iterator();
                while(it.hasNext()) {
                    sb.append(it.next());
                    if (it.hasNext()) {
                        sb.append(", ");
                    }
                }
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        String.format("Some field(s) are annotated with @%s, but no valid constructor is found: %s",
                                Option.class.getSimpleName(), sb),
                        typeElt);
            }
            return new OptionsMetaModel(typeElt, optionModels, constructorArgs);
        }
    }

    private abstract class TypeMetaModel {

        protected final TypeElement typeElt;
        protected final String pkg;

        TypeMetaModel(TypeElement typeElt) {
            if (typeElt != null) {
                this.pkg = processingEnv.getElementUtils().getPackageOf(typeElt).getQualifiedName().toString();
                this.typeElt = typeElt;
            } else {
                this.pkg = null;
                this.typeElt = null;
            }
        }
    }

    private class OptionMetaModel extends TypeMetaModel {

        private final OptionInfo option;

        OptionMetaModel(TypeElement typeElt, OptionInfo option) {
            super(typeElt);
            this.option = option;
        }
    }

    private class OptionsMetaModel extends TypeMetaModel {

        protected final Map<String, OptionMetaModel> options;
        protected final LinkedList<TypeMetaModel> constructorArgs;

        OptionsMetaModel(TypeElement typeElt, Map<String, OptionMetaModel> options, LinkedList<TypeMetaModel> constructorArgs) {
            super(typeElt);
            this.options = options;
            this.constructorArgs = constructorArgs;
        }

        OptionsMetaModel(OptionsMetaModel copy) {
            this(copy.typeElt, copy.options, copy.constructorArgs);
        }
    }

    private final class CommandMetaModel extends OptionsMetaModel {

        private final CommandInfo command;

        CommandMetaModel(OptionsMetaModel optionsModel, CommandInfo command) {
            super(optionsModel);
            this.command = command;
        }
    }
}

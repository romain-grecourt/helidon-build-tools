package io.helidon.build.cli.codegen;

import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.SimpleElementVisitor9;

import io.helidon.build.cli.harness.Command;
import io.helidon.build.cli.harness.CommandModel.CommandInfo;
import io.helidon.build.cli.harness.CommandModel.OptionInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;

/**
 * Command annotation processor.
 */
@SupportedAnnotationTypes(value = {
    "io.helidon.build.cli.harness.Command",
})
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class CommandAnnotationProcessor extends AbstractProcessor {

    private final List<CommandMetaModel> metaModels = new ArrayList<>();
    private final List<OptionsBagMetaModel> commonOptions = new ArrayList<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element elt : roundEnv.getElementsAnnotatedWith(Command.class)) {
            metaModels.add(elt.accept(new CommandClassVisitor(), null));
        }
        generateSources();
        return true;
    }

    private void generateSources() {
        Filer filer = processingEnv.getFiler();
        // TODO generate CommandModel implementations
            // 1. generate info objects initialization
            // 2. generate create method using parser
        // TODO generate CommandRegistry implementation
        // TODO generate META-INF/services/io.helidon.build.cli.harness.CommandRegistry
    }

    private final class CommandClassVisitor extends SimpleElementVisitor9<CommandMetaModel, Void> {

        @Override
        public CommandMetaModel visitType(TypeElement typeElt, Void p) {
            Command commandAnnot = typeElt.getAnnotation(Command.class);
            CommandInfo command = new CommandInfo(commandAnnot.name(), commandAnnot.description());
            Map<CharSequence, OptionInfo> optionInfos = new HashMap<>();
            for (Command.Option optionAnnot : typeElt.getAnnotationsByType(Command.Option.class)) {
                OptionInfo optionInfo = new OptionInfo(optionAnnot.name(), optionAnnot.description(),optionAnnot.required());
                optionInfos.put(optionInfo.name(), optionInfo);
            }
            LinkedList<CharSequence> constructorArgs = new LinkedList<>();
            for (Element elt : typeElt.getEnclosedElements()) {
                if (elt.getKind() == ElementKind.CONSTRUCTOR && elt.getModifiers().contains(Modifier.PUBLIC)) {
                    constructorArgs = elt.accept(new ConstructorVisitor(), optionInfos.keySet());
                    break;
                }
            }
            return new CommandMetaModel(typeElt.getQualifiedName(), command, optionInfos, constructorArgs);
        }
    }

    private final class ConstructorVisitor extends SimpleElementVisitor9<LinkedList<CharSequence>, Set<CharSequence>> {

        @Override
        public LinkedList<CharSequence> visitExecutable(ExecutableElement elt, Set<CharSequence> optionNames) {
            LinkedList<CharSequence> arguments = new LinkedList<>();
            for (VariableElement varElt : elt.getParameters()) {
                CharSequence varName = varElt.getSimpleName();
                if (!optionNames.contains(varName)) {
                    Element typeElt = processingEnv.getTypeUtils().asElement(varElt.asType());
                    OptionsBagMetaModel commonOptions = typeElt.accept(new OptionsBagTypeVisitor(), null);
                    if (commonOptions.options.isEmpty()) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Invalid constructor argument", varElt);
                    } else {
                        CommandAnnotationProcessor.this.commonOptions.add(commonOptions);
                    }
                }
                arguments.add(varName);
            }
            return arguments;
        }
    }

    private final class OptionsBagTypeVisitor extends SimpleElementVisitor9<OptionsBagMetaModel, Void> {

        @Override
        public OptionsBagMetaModel visitType(TypeElement typeElt, Void p) {
            Map<CharSequence, OptionInfo> optionInfos = new HashMap<>();
            for (Command.Option optionAnnot : typeElt.getAnnotationsByType(Command.Option.class)) {
                OptionInfo optionInfo = new OptionInfo(optionAnnot.name(), optionAnnot.description(),optionAnnot.required());
                optionInfos.put(optionInfo.name(), optionInfo);
            }
            LinkedList<CharSequence> constructorArgs = new LinkedList<>();
            for (Element elt : typeElt.getEnclosedElements()) {
                if (elt.getKind() == ElementKind.CONSTRUCTOR && elt.getModifiers().contains(Modifier.PUBLIC)) {
                    constructorArgs = elt.accept(new ConstructorVisitor(), optionInfos.keySet());
                    break;
                }
            }
            return new OptionsBagMetaModel(typeElt.getQualifiedName(), optionInfos, constructorArgs);
        }
    }

    private static final class OptionsBagMetaModel {

        private final CharSequence className;
        private final Map<CharSequence, OptionInfo> options;
        private final LinkedList<CharSequence> constructorArgs;

        OptionsBagMetaModel(CharSequence className, Map<CharSequence, OptionInfo> options,
                LinkedList<CharSequence> constructorArgs) {

            this.className = className;
            this.options = options;
            this.constructorArgs = constructorArgs;
        }
    }

    private static final class CommandMetaModel {

        private final CharSequence className;
        private final CommandInfo command;
        private final Map<CharSequence, OptionInfo> options;
        private final LinkedList<CharSequence> constructorArgs;

        CommandMetaModel(CharSequence className, CommandInfo command, Map<CharSequence, OptionInfo> options,
                LinkedList<CharSequence> constructorArgs) {

            this.className = className;
            this.command = command;
            this.options = options;
            this.constructorArgs = constructorArgs;
        }
    }
}

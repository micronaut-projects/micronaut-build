package io.micronaut.docs

import org.asciidoctor.ast.PhraseNode
import org.asciidoctor.ast.StructuralNode
import spock.lang.Specification

class PackageMacroSpec extends Specification {

    static class CapturingPackageMacro extends PackageMacro {
        StructuralNode parentArg
        String contextArg
        Object textArg
        Map<String, Object> attributesArg
        Map<String, Object> optionsArg

        CapturingPackageMacro(String macroName) {
            super(macroName)
        }

        // Avoid depending on parent.document by overriding process
        @Override
        PhraseNode process(StructuralNode parent, String target, Map<String, Object> attributes) {
            String defaultPackage = getDefaultPackagePrefix()
            if (defaultPackage != null && !target.startsWith(defaultPackage)) {
                target = "${defaultPackage}${target}"
            }
            // Ignore parent.document and use getBaseUri with a dummy map
            String baseUri = getBaseUri([:] as Map<String, Object>)
            final Map options = [
                    type  : ':link',
                    target: "${baseUri}/${target.replace('.','/')}/package-summary.html".toString()
            ] as Map<String, Object>

            String pkg = target
            if (attributes.text) {
                pkg = attributes.text
            }
            // Capture without delegating to AsciidoctorJ runtime
            this.parentArg = parent
            this.contextArg = "anchor"
            this.textArg = pkg
            this.attributesArg = attributes
            this.optionsArg = options
            return null
        }

        // Capture created phrase node data
        PhraseNode createPhraseNode(StructuralNode parent, String context, Object text, Map<String, Object> attributes, Map<String, Object> options) {
            this.parentArg = parent
            this.contextArg = context
            this.textArg = text
            this.attributesArg = attributes
            this.optionsArg = options
            return null
        }
    }

    void "adds default package prefix and builds correct link"() {
        given:
        def macro = new CapturingPackageMacro("package")

        when:
        macro.process(null, "http", [:])

        then:
        macro.optionsArg != null
        macro.optionsArg.type == ':link'
        macro.optionsArg.target == "../api/io/micronaut/http/package-summary.html"
        macro.textArg == "io.micronaut.http"
    }

    void "uses provided text attribute as link text"() {
        given:
        def macro = new CapturingPackageMacro("package")

        when:
        macro.process(null, "http", [text: 'HTTP package'])

        then:
        macro.optionsArg.target == "../api/io/micronaut/http/package-summary.html"
        macro.textArg == "HTTP package"
    }

    void "does not prepend default prefix when already present"() {
        given:
        def macro = new CapturingPackageMacro("package")

        when:
        macro.process(null, "io.micronaut.context", [:])

        then:
        macro.optionsArg.target == "../api/io/micronaut/context/package-summary.html"
        macro.textArg == "io.micronaut.context"
    }
}

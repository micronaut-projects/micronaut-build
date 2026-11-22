package io.micronaut.docs

import spock.lang.Specification

class DocPublisherLanguageCssSpec extends Specification {

    void "the guide of one language hides the others and shows its own last"() {
        when:
        String css = DocPublisher.languageCss("python")
        List<String> rules = rules(css)

        then: "every rule is valid: nothing stands between them but white space"
        css.replaceAll(/\.lang-[a-z]+ \{ display: (none|block) \}/, "").trim().isEmpty()

        and: "every other language is hidden"
        LanguageSnippetMacro.LANGS.findAll { it != "python" }.every { rules.contains(".lang-$it { display: none }".toString()) }

        and: "the rule that shows the language comes last, so a block marked for it and another is shown"
        rules.last() == ".lang-python { display: block }"
        rules.count { it.startsWith(".lang-python ") } == 1
    }

    void "a language in the middle of the list is still shown last"() {
        expect:
        LanguageSnippetMacro.LANGS.size() > 2
        String middle = LanguageSnippetMacro.LANGS[1]
        rules(DocPublisher.languageCss(middle)).last() == ".lang-$middle { display: block }".toString()
    }

    void "the guide of every language leaves the text of each to its language selector"() {
        expect: "no rule that would override the hidden class the selector adds"
        DocPublisher.languageCss(null) == ""
        DocPublisher.languageCss("") == ""
    }

    private static List<String> rules(String css) {
        (css =~ /\.lang-[a-z]+ \{ display: [a-z]+ \}/).collect { it as String }
    }
}

package io.micronaut.build.catalogs.internal

import spock.lang.Specification

import java.nio.charset.StandardCharsets

class VersionlessLibraryParserTest extends Specification {
    void 'settings can retain versionless coordinates without changing existing rich models'() {
        given:
        def standard = new LenientVersionCatalogParser()
        def settings = new LenientVersionCatalogParser(true)

        when:
        standard.parse(catalog())
        settings.parse(catalog())

        then:
        !standard.model.findLibrary('example', 'versionless').present
        settings.model.findLibrary('example', 'versionless').get().version.version == RichVersion.EMPTY
        standard.model.versionsTable == settings.model.versionsTable
        standard.model.librariesTable.every { existing -> settings.model.librariesTable.contains(existing) }
        settings.model.librariesTable.size() == standard.model.librariesTable.size() + 1
    }

    private static InputStream catalog() {
        new ByteArrayInputStream('''
            [versions]
            rich = { strictly = "[1,3[", prefer = "2", reject = ["2.1"] }
            [libraries]
            plain = "example:plain:1.0"
            strict = { module = "example:strict", version = { strictly = "1.0" } }
            preferred = { module = "example:preferred", version = { prefer = "2.0" } }
            reference = { module = "example:reference", version.ref = "rich" }
            versionless = { module = "example:versionless" }
        '''.getBytes(StandardCharsets.UTF_8))
    }
}

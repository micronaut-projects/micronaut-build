package io.micronaut.build.catalogs

import groovy.json.JsonOutput
import io.micronaut.build.AbstractFunctionalTest

class CoreCatalogFallbackFunctionalTest extends AbstractFunctionalTest {
    private static final String MODULE = 'io.micronaut.example:micronaut-example-bom'
    private static final String PLATFORM_MODULE = 'io.micronaut.reactor:micronaut-reactor-bom'

    void 'existing #kind version takes precedence without resolving core'() {
        given:
        setupProject("""
            [versions]
            micronaut = "99.0"
            micronaut-platform = "99.0"
            example = $versionEntry
            [libraries]
            example = { module = "$MODULE", $declaration }
        """)

        when:
        run 'verifyCatalog', '--offline'

        then:
        outputContains 'Catalog marker: 1.0'

        where:
        kind             | versionEntry                                      | declaration
        'inline'         | '"1.0"'                                           | 'version = "1.0"'
        'reference'      | '"1.0"'                                           | 'version.ref = "example"'
        'rich inline'    | '"1.0"'                                           | 'version = { require = "1.0", prefer = "2.0" }'
        'rich reference' | '{ require = "1.0", strictly = "1.0", reject = ["3.0"] }' | 'version.ref = "example"'
    }

    void 'versionless #notation declaration falls back to core and supports configuration cache'() {
        given:
        setupProject("""
            [versions]
            micronaut = "5.0"
            [libraries]
            example = { $declaration }
        """)

        when:
        run 'verifyCatalog', '--offline', '--configuration-cache'

        then:
        outputContains 'Catalog marker: 2.0'

        when:
        run 'verifyCatalog', '--offline', '--configuration-cache'

        then:
        outputContains 'Reusing configuration cache.'
        outputContains 'Catalog marker: 2.0'

        where:
        notation     | declaration
        'module'     | 'module = "io.micronaut.example:micronaut-example-bom"'
        'group-name' | 'group = "io.micronaut.example", name = "micronaut-example-bom"'
    }

    void 'missing version throws when core does not manage the module'() {
        given:
        setupProject('''
            [versions]
            micronaut = "5.0"
            [libraries]
            example = { module = "io.micronaut.missing:micronaut-example-bom" }
        ''')

        when:
        fails 'help', '--offline'

        then:
        errorOutputContains 'Micronaut core BOM 5.0 does not supply a catalog version for io.micronaut.missing:micronaut-example-bom'
    }

    void 'platform takes precedence without resolving core'() {
        given:
        setupProject("""
            [versions]
            micronaut = "99.0"
            micronaut-platform = "3.0"
            [libraries]
            example = { module = "$MODULE" }
        """)
        publishCatalog(MODULE, '3.0', '[libraries]\nmarker = "example:marker:3.0"')
        file('repo/io/micronaut/platform/micronaut-platform/3.0/micronaut-platform-3.0.toml') << """
            boms-example = { module = "$MODULE", version = "3.0" }
        """

        when:
        run 'verifyCatalog', '--offline'

        then:
        outputContains 'Catalog marker: 3.0'
    }

    void 'core supplies a version when the selected platform has no entry'() {
        given:
        setupProject("""
            [versions]
            micronaut = "5.0"
            micronaut-platform = "3.0"
            [libraries]
            example = { module = "$MODULE" }
        """)

        when:
        run 'verifyCatalog', '--offline'

        then:
        outputContains 'Catalog marker: 2.0'
    }

    void 'platform supplies a module absent from core using different aliases and supports configuration cache'() {
        given:
        setupProject("""
            [versions]
            micronaut = "5.0"
            micronaut-platform = "3.0"
            [libraries]
            example = { module = "$PLATFORM_MODULE" }
        """, 'mnReactor')

        when:
        run 'verifyCatalog', '--offline', '--configuration-cache'

        then:
        outputContains 'Catalog marker: 4.0'

        when:
        run 'verifyCatalog', '--offline', '--configuration-cache'

        then:
        outputContains 'Reusing configuration cache.'
        outputContains 'Catalog marker: 4.0'
    }

    void 'platform can be selected by Gradle property without a core version for a named import'() {
        given:
        setupProject("""
            [libraries]
            example = { module = "$PLATFORM_MODULE" }
        """, 'mnReactor')

        when:
        run 'verifyCatalog', '--offline', '-PmicronautPlatformVersion=3.0'

        then:
        outputContains 'Catalog marker: 4.0'
    }

    void 'missing module reports both searched catalogs'() {
        given:
        setupProject('''
            [versions]
            micronaut = "5.0"
            micronaut-platform = "3.0"
            [libraries]
            example = { module = "io.micronaut.missing:micronaut-example-bom" }
        ''')

        when:
        fails 'help', '--offline'

        then:
        errorOutputContains 'Micronaut platform BOM 3.0 and core BOM 5.0 do not supply a catalog version for io.micronaut.missing:micronaut-example-bom'
    }

    void 'missing core version reports how to configure it'() {
        given:
        setupProject("""
            [libraries]
            example = { module = "$MODULE" }
        """)

        when:
        fails 'help', '--offline'

        then:
        errorOutputContains "No version for catalog 'example'"
        errorOutputContains '[versions].micronaut or micronautVersion'
    }

    void 'unknown library alias still fails'() {
        given:
        setupProject('[versions]\nmicronaut = "5.0"')

        when:
        fails 'help', '--offline'

        then:
        errorOutputContains "Version catalog doesn't contain a library with alias: example"
    }

    private void setupProject(String catalog, String catalogName = 'mnExample') {
        publishCatalog(MODULE, '1.0', '[libraries]\nmarker = "example:marker:1.0"')
        publishCatalog(MODULE, '2.0', '[libraries]\nmarker = "example:marker:2.0"')
        publishCatalog(PLATFORM_MODULE, '4.0', '[libraries]\nmarker = "example:marker:4.0"')
        publishCatalog('io.micronaut.platform:micronaut-platform', '3.0', """
            [versions]
            managed-micronaut-reactor = "4.0"
            [libraries]
            boms-micronaut-reactor = { module = "$PLATFORM_MODULE", version.ref = "managed-micronaut-reactor" }
        """)
        publishCatalog('io.micronaut:micronaut-core-bom', '5.0', """
            [versions]
            example = "2.0"
            [libraries]
            different-alias = { module = "$MODULE", version.ref = "example" }
        """)
        file('gradle/libs.versions.toml').text = catalog
        settingsFile.text = '''
            plugins { id 'io.micronaut.build.shared.settings' }
            rootProject.name = 'core-catalog-fallback'
            dependencyResolutionManagement {
                repositories { maven { url = uri('repo') } }
            }
            micronautBuild { importMicronautCatalog('example') }
        '''
        buildFile.text = '''
            plugins { id 'java' }
            def marker = CATALOG.marker.map { it.versionConstraint.requiredVersion }
            tasks.register('verifyCatalog') {
                inputs.property('marker', marker)
                doLast { println "Catalog marker: ${inputs.properties.marker}" }
            }
            assert configurations.findByName('globalBoms') == null
        '''.replace('CATALOG', catalogName)
    }

    private void publishCatalog(String module, String version, String content) {
        def (group, artifact) = module.split(':')
        String directory = "repo/${group.replace('.', '/')}/$artifact/$version"
        String base = "$artifact-$version"
        file("$directory/${base}.toml").text = content
        file("$directory/${base}.pom").text = """
            <project>
              <!-- do_not_remove: published-with-gradle-metadata -->
              <modelVersion>4.0.0</modelVersion>
              <groupId>$group</groupId><artifactId>$artifact</artifactId><version>$version</version>
              <packaging>pom</packaging>
            </project>
        """
        file("$directory/${base}.module").text = JsonOutput.toJson([
            formatVersion: '1.1',
            component: [group: group, module: artifact, version: version],
            variants: [[
                name: 'versionCatalogElements',
                attributes: ['org.gradle.category': 'platform', 'org.gradle.usage': 'version-catalog'],
                files: [[name: "${base}.toml", url: "${base}.toml"]]
            ]]
        ])
    }
}

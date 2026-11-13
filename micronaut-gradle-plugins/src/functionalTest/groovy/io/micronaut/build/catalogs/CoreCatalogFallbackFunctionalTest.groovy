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

    void 'BOM inclusion is #description for common projects'() {
        given:
        setupProject("""
            [versions]
            micronaut = "5.0"
            [libraries]
            example = { module = "$MODULE", version = "1.0" }
        """)
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example')", "importMicronautCatalog('example'$argument)")
        setupBomConsumer(enableBom, included, '1.0')

        when:
        run 'verifyCatalog', ':consumer:verifyBom', '--offline', '--configuration-cache'

        then:
        outputContains 'Catalog marker: 1.0'
        outputContains "BOM included: $included"

        when:
        run 'verifyCatalog', ':consumer:verifyBom', '--offline', '--configuration-cache'

        then:
        outputContains 'Reusing configuration cache.'
        outputContains "BOM included: $included"

        where:
        description                  | argument  | enableBom | included
        'disabled by default'        | ''        | true      | false
        'explicitly disabled'        | ', false' | true      | false
        'explicitly enabled'         | ', true'  | true      | true
        'disabled by project switch' | ', true'  | false     | false
    }

    void 'BOM inclusion uses the same inferred version as the imported catalog'() {
        given:
        setupProject("""
            [versions]
            micronaut = "5.0"
            [libraries]
            example = { module = "$MODULE" }
        """)
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example')", "importMicronautCatalog('example', true)")
        setupBomConsumer(true, true, '2.0')

        when:
        run 'verifyCatalog', ':consumer:verifyBom', '--offline'

        then:
        outputContains 'Catalog marker: 2.0'
        outputContains 'BOM included: true'
    }

    void 'platform own constraints can be applied explicitly'() {
        given:
        setupProject('''
            [versions]
            micronaut = "5.0"
            micronaut-platform = "3.0"
            [libraries]
            example = { module = "io.micronaut.platform:micronaut-platform", version.ref = "micronaut-platform" }
        ''', 'mnPlatform')
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example')", "importMicronautCatalog('example', true)")
        setupBomConsumer(true, true, '3.0', 'io.micronaut.platform:micronaut-platform')

        when:
        run 'verifyCatalog', ':consumer:verifyBom', '--offline'

        then:
        outputContains 'Catalog marker: 3.0'
        outputContains 'BOM included: true'
    }

    void 'configured platform is included automatically with enableBom #enabled'() {
        given:
        setupProject("""
            [versions]
            micronaut = "5.0"
            micronaut-platform = "3.0"
            [libraries]
            example = { module = "$MODULE", version = "1.0" }
        """)
        setupBomConsumer(enabled, enabled, '3.0', 'io.micronaut.platform:micronaut-platform')

        when:
        run 'verifyCatalog', ':consumer:verifyBom', '--offline', '--configuration-cache'

        then:
        outputContains 'Catalog marker: 1.0'
        outputContains "BOM included: $enabled"

        when:
        run 'verifyCatalog', ':consumer:verifyBom', '--offline', '--configuration-cache'

        then:
        outputContains 'Reusing configuration cache.'

        where:
        enabled << [true, false]
    }

    void 'BOM coverage reaches Java projects with platform #platform enabled #enabled and existing extension #extension'() {
        given:
        setupProject("""
            [versions]
            micronaut = "5.0"
            ${platform ? 'micronaut-platform = "3.0"' : ''}
            [libraries]
            example = { module = "$MODULE", version = "1.0" }
        """)
        if (!platform) {
            settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example')", "importMicronautCatalog('example', true)")
        }
        setupBomConsumer(enabled, enabled, platform ? '3.0' : '1.0',
            platform ? 'io.micronaut.platform:micronaut-platform' : MODULE)
        def consumer = file('consumer/build.gradle')
        consumer.text = consumer.text.replace("id 'io.micronaut.build.internal.common'",
            extension ? "id 'java-library'; id 'io.micronaut.build.internal.java-base'" : "id 'java-library'")
            .replace('inputs.properties.coreEnabled == ' + enabled, 'inputs.properties.coreEnabled == false')

        when:
        run ':consumer:verifyBom', '--offline', '--configuration-cache'

        then:
        outputContains "BOM included: $enabled"

        when:
        run ':consumer:verifyBom', '--offline', '--configuration-cache'

        then:
        outputContains 'Reusing configuration cache.'

        where:
        platform | enabled | extension
        true     | true    | true
        true     | false   | true
        false    | true    | true
        false    | false   | true
        true     | true    | false
        true     | false   | false
        false    | true    | false
        false    | false   | false
    }

    void 'configured platform property is also automatically applied'() {
        given:
        setupProject("""
            [versions]
            micronaut = "5.0"
            [libraries]
            example = { module = "$MODULE", version = "1.0" }
        """)
        setupBomConsumer(true, true, '3.0', 'io.micronaut.platform:micronaut-platform')

        when:
        run 'verifyCatalog', ':consumer:verifyBom', '--offline', '-PmicronautPlatformVersion=3.0'

        then:
        outputContains 'BOM included: true'
    }

    void 'automatic platform also reaches applications without an explicit platform import'() {
        given:
        setupProject("""
            [versions]
            micronaut = "5.0"
            micronaut-platform = "3.0"
            [libraries]
            example = { module = "$MODULE", version = "1.0" }
        """)
        setupBomConsumer(true, true, '3.0', 'io.micronaut.platform:micronaut-platform', true)

        when:
        run 'verifyCatalog', ':consumer:verifyBom', '--offline'

        then:
        outputContains 'BOM included: true'
    }

    void 'unconfigured platform requires explicit true with argument #argument'() {
        given:
        setupProject('''
            [libraries]
            example = { module = "io.micronaut.platform:micronaut-platform", version = "3.0" }
        ''', 'mnPlatform')
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example')", "importMicronautCatalog('example'$argument)")

        when:
        fails 'help', '--offline'

        then:
        errorOutputContains 'The platform must be imported using importMicronautCatalog(alias, true).'

        where:
        argument << ['', ', false']
    }

    void 'included BOMs also reach the existing application BOM bucket'() {
        given:
        setupProject("""
            [versions]
            micronaut = "5.0"
            [libraries]
            example = { module = "$MODULE", version = "1.0" }
        """)
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example')", "importMicronautCatalog('example', true)")
        setupBomConsumer(true, true, '1.0', MODULE, true)

        when:
        run 'verifyCatalog', ':consumer:verifyBom', '--offline', '--configuration-cache'

        then:
        outputContains 'BOM included: true'

        when:
        run 'verifyCatalog', ':consumer:verifyBom', '--offline', '--configuration-cache'

        then:
        outputContains 'Reusing configuration cache.'
        outputContains 'BOM included: true'
    }

    private void setupBomConsumer(boolean enableBom, boolean included, String version,
                                  String includedModule = MODULE, boolean application = false) {
        settingsFile << "\ninclude 'consumer'\n"
        file('gradle.properties').text = 'projectVersion=1.0.0-SNAPSHOT\n'
        file("repo/example/managed/$version/managed-${version}.pom").text = """
            <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>
            <artifactId>managed</artifactId><version>$version</version></project>
        """
        file('consumer/build.gradle').text = '''
            plugins { id 'io.micronaut.build.internal.common' }
            repositories { maven { url = rootProject.file('repo') } }
            micronautBuild {
                enableBom = ENABLE_BOM
                enableProcessing = false
            }
            dependencies {
                implementation 'example:managed'
                annotationProcessor 'example:managed'
                testImplementation 'example:managed'
                testAnnotationProcessor 'example:managed'
            }
            def probes = ['compileClasspath', 'runtimeClasspath', 'testCompileClasspath',
                          'testRuntimeClasspath', 'annotationProcessor', 'testAnnotationProcessor']
                .collectEntries { name -> [name, configurations[name].incoming.resolutionResult.rootComponent] }
            def coreEnabled = configurations.globalBoms.allDependencies.any {
                it.group == 'io.micronaut' && it.name == 'micronaut-core-bom'
            }
            def extraCount = configurations.globalBoms.allDependencies.count {
                it.group == 'io.micronaut.example' && it.name == 'micronaut-example-bom'
            }
            tasks.register('verifyBom') {
                inputs.property('coreEnabled', coreEnabled)
                inputs.property('extraCount', extraCount)
                doLast {
                    assert inputs.properties.coreEnabled == ENABLE_BOM
                    assert inputs.properties.extraCount == (INCLUDED ? 1 : 0)
                    probes.each { name, root ->
                        def dependency = root.get().dependencies.find { it.requested.displayName.startsWith('example:managed') }
                        assert dependency != null: name
                        if (INCLUDED) {
                            assert dependency instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult: name
                            assert dependency.selected.moduleVersion.version == 'EXPECTED_VERSION': name
                        } else {
                            assert dependency instanceof org.gradle.api.artifacts.result.UnresolvedDependencyResult: name
                        }
                    }
                    println 'BOM included: INCLUDED'
                }
            }
        '''.replace('ENABLE_BOM', enableBom.toString()).replace('INCLUDED', included.toString()).replace('EXPECTED_VERSION', version)
        def (bomGroup, bomName) = includedModule.split(':')
        def consumer = file('consumer/build.gradle')
        consumer.text = consumer.text.replace('io.micronaut.example', bomGroup).replace('micronaut-example-bom', bomName)
        if (application) {
            consumer.text = consumer.text.replace('io.micronaut.build.internal.common', 'io.micronaut.build.internal.java-base')
                .replace('globalBoms', 'micronautBoms')
                .replace('micronautBuild {', '''
                    configurations {
                        micronautBoms { canBeResolved = false; canBeConsumed = false }
                        implementation.extendsFrom(micronautBoms)
                        annotationProcessor.extendsFrom(micronautBoms)
                        testAnnotationProcessor.extendsFrom(micronautBoms)
                    }
                    dependencies { micronautBoms platform('io.micronaut:micronaut-core-bom:5.0') }
                    micronautBuild {
                ''')
        }
    }

    private void setupProject(String catalog, String catalogName = 'mnExample') {
        publishCatalog(MODULE, '1.0', '[libraries]\nmarker = "example:marker:1.0"')
        publishCatalog(MODULE, '2.0', '[libraries]\nmarker = "example:marker:2.0"')
        publishCatalog(PLATFORM_MODULE, '4.0', '[libraries]\nmarker = "example:marker:4.0"')
        publishCatalog('io.micronaut.platform:micronaut-platform', '3.0', """
            [versions]
            managed-micronaut-reactor = "4.0"
            [libraries]
            marker = "example:marker:3.0"
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
            ]] + ['java-api', 'java-runtime'].collect { usage -> [
                name: usage,
                attributes: ['org.gradle.category': 'platform', 'org.gradle.usage': usage],
                dependencyConstraints: module in [MODULE, 'io.micronaut.platform:micronaut-platform'] ?
                    [[group: 'example', module: 'managed', version: [requires: version]]] : []
            ] }
        ])
    }
}

package io.micronaut.build.catalogs

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import io.micronaut.build.AbstractFunctionalTest

class CoreCatalogFallbackFunctionalTest extends AbstractFunctionalTest {
    private static final String MODULE = 'io.micronaut.example:micronaut-example-bom'
    private static final String PLATFORM_MODULE = 'io.micronaut.reactor:micronaut-reactor-bom'

    void 'test and logging catalogs use #source versions with optional overrides'() {
        given:
        setupProject("""
            [versions]
            ${platformProperty == false ? 'micronaut-platform = "3.0"' : ''}
            $selectors
        """, 'mn')
        ['5.0', '6.0', '7.0'].each { v ->
            publishCatalog('io.micronaut:micronaut-core-bom', v, "[libraries]\nmarker = \"example:marker:$v\"")
            def metadataFile = file("repo/io/micronaut/micronaut-core-bom/$v/micronaut-core-bom-${v}.module")
            def metadata = new JsonSlurper().parse(metadataFile)
            metadata.variants.findAll { it.attributes['org.gradle.usage'] != 'version-catalog' }.each {
                it.dependencyConstraints = [[group: 'example', module: 'core-library', version: [requires: v]]]
            }
            metadataFile.text = JsonOutput.toJson(metadata)
            file("repo/example/core-library/$v/core-library-${v}.pom").text = """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>
                <artifactId>core-library</artifactId><version>$v</version></project>
            """
        }
        ['1.0', '2.0', '3.0'].each { v ->
            publishCatalog('io.micronaut.test:micronaut-test-bom', v, "[libraries]\nmarker = \"example:marker:$v\"")
            publishCatalog('io.micronaut.logging:micronaut-logging-bom', v, "[libraries]\nmarker = \"example:marker:$v\"")
        }
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example', false)", 'importMicronautCatalog()')
        settingsFile << "\ninclude 'consumer'\n"
        file('gradle.properties').text = 'projectVersion=1.0.0-SNAPSHOT\n'
        file('consumer/build.gradle').text = """
            plugins { id 'io.micronaut.build.internal.common' }
            repositories { maven { url = rootProject.file('repo') } }
            micronautBuild { enableProcessing = false }
            dependencies { implementation 'example:core-library' }
            def catalogs = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension)
            def versions = ['mn', 'mnTest', 'mnLogging'].collectEntries { name ->
                [name, catalogs.named(name).findLibrary('marker').get().get().versionConstraint.requiredVersion]
            }
            def declaredCore = configurations.globalBoms.allDependencies.find { it.name == 'micronaut-core-bom' }.version
            def effectiveCoreProperty = project.property('micronautVersion')
            def graph = configurations.compileClasspath.incoming.resolutionResult.rootComponent
            tasks.register('verifyBaseCatalogs') {
                inputs.property('catalogs', versions)
                inputs.property('coreBom', declaredCore)
                inputs.property('coreProperty', effectiveCoreProperty)
                doLast {
                    assert inputs.properties.catalogs == [mn: '$core', mnTest: '$test', mnLogging: '$logging']
                    assert inputs.properties.coreBom == '$core'
                    assert inputs.properties.coreProperty == '${properties.find { it.startsWith('-PmicronautVersion=') }?.split('=', 2)?.last() ?: core}'
                    def library = graph.get().dependencies.find { it.requested.displayName == 'example:core-library' }
                    assert library instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult
                    assert library.selected.moduleVersion.version == '$core'
                    println 'Base catalogs and core conventions verified'
                }
            }
        """
        def options = ['--offline', '--configuration-cache'] + properties +
            (platformProperty ? ['-PmicronautPlatformVersion=3.0'] : [])

        when:
        run(*([':consumer:verifyBaseCatalogs'] + options))

        then:
        outputContains 'Base catalogs and core conventions verified'

        when:
        run(*([':consumer:verifyBaseCatalogs'] + options))

        then:
        outputContains 'Reusing configuration cache.'

        where:
        source              | platformProperty | selectors                                                                                     | properties                                                                                  | core  | test  | logging
        'platform only'     | false            | ''                                                                                            | []                                                                                          | '5.0' | '1.0' | '1.0'
        'platform property only' | true         | ''                                                                                            | []                                                                                          | '5.0' | '1.0' | '1.0'
        'platform'          | false            | 'micronaut = "5.0"'                                                                                            | []                                                                                          | '5.0' | '1.0' | '1.0'
        'platform property' | true             | 'micronaut = "5.0"'                                                                                            | []                                                                                          | '5.0' | '1.0' | '1.0'
        'local selectors'   | false            | 'micronaut = "6.0"\nmicronaut-test = "2.0"\nmicronaut-logging = "2.0"'                          | []                                                                                          | '6.0' | '2.0' | '2.0'
        'Gradle properties' | false            | ''                                                                                            | ['-PmicronautVersion=6.0', '-PmicronautTestVersion=2.0', '-PmicronautLoggingVersion=2.0']         | '6.0' | '2.0' | '2.0'
        'TOML precedence'   | false            | 'micronaut = "6.0"\nmicronaut-test = "2.0"\nmicronaut-logging = "2.0"'                          | ['-PmicronautVersion=7.0', '-PmicronautTestVersion=3.0', '-PmicronautLoggingVersion=3.0']         | '6.0' | '2.0' | '2.0'
        'mixed overrides'   | false            | 'micronaut = "6.0"\nmicronaut-test = "1.0"'                                                                           | []                                                                                          | '6.0' | '1.0' | '1.0'
        'legacy selectors'  | null             | 'micronaut = "5.0"\nmicronaut-test = "1.0"\nmicronaut-logging = "1.0"'                          | []                                                                                          | '5.0' | '1.0' | '1.0'
    }

    void 'configured platform missing base BOM #module fails clearly'() {
        given:
        setupProject('[versions]\nmicronaut-platform = "3.0"', 'mn')
        def platform = file('repo/io/micronaut/platform/micronaut-platform/3.0/micronaut-platform-3.0.toml')
        platform.text = platform.text.readLines().findAll { !it.contains(module) }.join('\n') + '\n'
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example', false)", 'importMicronautCatalog()')

        when:
        fails 'help', '--offline'

        then:
        errorOutputContains "does not supply a catalog version for $module"
        errorOutputContains "Declare [versions].$selector"

        where:
        module                                             | selector
        'io.micronaut:micronaut-core-bom'                    | 'micronaut'
        'io.micronaut.test:micronaut-test-bom'                | 'micronaut-test'
        'io.micronaut.logging:micronaut-logging-bom'          | 'micronaut-logging'
    }

    void 'predeclared test and logging catalogs retain their existing versions'() {
        given:
        setupProject('[versions]\nmicronaut-platform = "3.0"', 'mnTest')
        def platform = file('repo/io/micronaut/platform/micronaut-platform/3.0/micronaut-platform-3.0.toml')
        platform.text = platform.text.readLines().findAll {
            !it.contains('io.micronaut.test:micronaut-test-bom') && !it.contains('io.micronaut.logging:micronaut-logging-bom')
        }.join('\n') + '\n'
        settingsFile.text = settingsFile.text.replace("micronautBuild { importMicronautCatalog('example', false) }", '''
            dependencyResolutionManagement {
                versionCatalogs {
                    create('mnTest') { from('io.micronaut.test:micronaut-test-bom:1.0') }
                    create('mnLogging') { from('io.micronaut.logging:micronaut-logging-bom:1.0') }
                }
            }
            micronautBuild { importMicronautCatalog() }
        ''')

        when:
        run 'verifyCatalog', '--offline'

        then:
        outputContains 'Catalog marker: 1.0'
    }

    void 'test and logging retain built-in defaults without a platform'() {
        given:
        setupProject('[versions]\nmicronaut = "5.0"', 'mnTest')
        settingsFile.text = settingsFile.text.replace("micronautBuild { importMicronautCatalog('example', false) }", '''
            // Publish local fixture catalogs at the generated built-in versions,
            // without hard-coding release versions or relying on a machine cache.
            [
                'io.micronaut.test:micronaut-test-bom': io.micronaut.build.utils.DefaultVersions.MICRONAUT_TEST_VERSION,
                'io.micronaut.logging:micronaut-logging-bom': io.micronaut.build.utils.DefaultVersions.MICRONAUT_LOGGING_VERSION
            ].each { module, version ->
                def (group, artifact) = module.split(':')
                def base = 'repo/' + group.replace('.', '/') + '/' + artifact
                def template = new File(settingsDir, base + '/1.0')
                def target = new File(settingsDir, base + '/' + version)
                target.mkdirs()
                template.listFiles().each { input ->
                    def output = new File(target, input.name.replace('-1.0.', '-' + version + '.'))
                    output.text = input.name.endsWith('.toml') ? input.text : input.text.replace('1.0', version)
                }
            }
            micronautBuild { importMicronautCatalog() }
        ''')
        buildFile << "\nassert mnLogging.marker.get().versionConstraint.requiredVersion == '1.0'\n"

        when:
        run 'verifyCatalog', '--offline'

        then:
        outputContains 'Catalog marker: 1.0'
    }

    void 'BOM precedence #localVersion with #notation include #includeBom enabled #enabled local pin #localPin removal #removal'() {
        given:
        setupProject("""
            [versions]
            micronaut = "5.0"
            micronaut-platform = "3.0"
            reactor = "$localVersion"
            [libraries]
            micronaut-reactor = { module = "$PLATFORM_MODULE"${localPin ? ', version.ref = "reactor"' : ''} }
        """, 'mnReactor')
        publishCatalog(PLATFORM_MODULE, '4.0', '[libraries]\nmarker = "example:marker:4.0"', true)
        publishCatalog(PLATFORM_MODULE, localVersion, "[libraries]\nmarker = \"example:marker:$localVersion\"", true)
        def platformMetadata = file('repo/io/micronaut/platform/micronaut-platform/3.0/micronaut-platform-3.0.module')
        def metadata = new JsonSlurper().parse(platformMetadata)
        metadata.variants.findAll { it.attributes['org.gradle.usage'] != 'version-catalog' }.each {
            // Both the platform's imported BOM and a platform-owned constraint
            // disagree with a local downgrade. The local override must win both.
            it.dependencies = [[group: 'io.micronaut.reactor', module: 'micronaut-reactor-bom', version: [requires: '4.0'],
                                attributes: ['org.gradle.category': 'platform']]]
        }
        platformMetadata.text = JsonOutput.toJson(metadata)
        String argument = notation == 'enum' ? 'io.micronaut.build.MicronautBom.REACTOR' : "'micronaut-reactor'"
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example', false)",
            "importMicronautCatalog($argument${includeBom == null ? '' : ', ' + includeBom})")
        settingsFile << "\ninclude 'consumer'\n"
        file('gradle.properties').text = 'projectVersion=1.0.0-SNAPSHOT\ntitle=Local BOM override fixture\n'
        String expected = enabled && includeBom != false ? (localPin ? localVersion : '4.0') : '5.0'
        file("repo/example/managed/$expected/managed-${expected}.pom").text = """
            <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>
            <artifactId>managed</artifactId><version>$expected</version></project>
        """
        new java.util.jar.JarOutputStream(new FileOutputStream(file("repo/example/managed/$expected/managed-${expected}.jar"))).close()
        file('consumer/build.gradle').text = """
            plugins {
                id '${application ? 'io.micronaut.build.internal.java-base' : 'io.micronaut.build.internal.common'}'
                id 'java-library'
                id 'maven-publish'
            }
            group = 'example'
            version = '1.0'
            repositories { maven { url = rootProject.file('repo') } }
            ${application ? '''configurations {
                micronautBoms { canBeResolved = false; canBeConsumed = false }
                implementation.extendsFrom(micronautBoms)
                annotationProcessor.extendsFrom(micronautBoms)
                testAnnotationProcessor.extendsFrom(micronautBoms)
            }''' : ''}
            micronautBuild { enableBom = $enabled; enableProcessing = false }
            dependencies {
                implementation 'example:managed:5.0'
                annotationProcessor 'example:managed:5.0'
                testImplementation 'example:managed:5.0'
                testAnnotationProcessor 'example:managed:5.0'
            }
            publishing { publications { sample(MavenPublication) { from components.java } } }
            def probes = ['compileClasspath', 'runtimeClasspath', 'testCompileClasspath',
                          'testRuntimeClasspath', 'annotationProcessor', 'testAnnotationProcessor']
                .collectEntries { name -> [name, configurations[name].incoming.resolutionResult.rootComponent] }
            tasks.register('verifyLocalOverride') {
                outputs.file(layout.buildDirectory.file('bom-graph.json'))
                outputs.upToDateWhen { false }
                doLast {
                    def snapshot = [:]
                    probes.each { name, root ->
                        def dependency = root.get().dependencies.find { it.requested.displayName.startsWith('example:managed') }
                        assert dependency instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult: name
                        assert dependency.selected.moduleVersion.version == '$expected': name
                        def queue = [root.get()]
                        def components = [] as Set
                        while (!queue.empty) {
                            def component = queue.remove(0)
                            if (components.add(component.id.displayName)) {
                                queue.addAll(component.dependencies.findAll {
                                    it instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult
                                }.collect { it.selected })
                            }
                        }
                        snapshot[name] = components.sort()
                    }
                    outputs.files.singleFile.text = groovy.json.JsonOutput.toJson(snapshot)
                    println 'Local BOM override verified'
                }
            }
        """

        when:
        run(*([':consumer:verifyLocalOverride'] + (enabled ? [':consumer:generateMetadataFileForSamplePublication'] : []) +
            ['--offline', '--configuration-cache']))

        then:
        outputContains 'Local BOM override verified'
        if (enabled) {
            def published = new JsonSlurper().parse(file('consumer/build/publications/sample/module.json'))
            assert published.variants.every { variant ->
                (variant.dependencies ?: []).every { it.attributes?.get('org.gradle.category') != 'enforced-platform' }
            }
        }

        when:
        run(*([':consumer:verifyLocalOverride'] + (enabled ? [':consumer:generateMetadataFileForSamplePublication'] : []) +
            ['--offline', '--configuration-cache']))

        then:
        outputContains 'Reusing configuration cache.'

        when:
        def beforeRemoval = file('consumer/build/bom-graph.json').text
        if (removal) {
            def catalog = file('gradle/libs.versions.toml')
            catalog.text = removal == 'declaration' ?
                catalog.text.readLines().findAll { !it.trim().startsWith('micronaut-reactor =') }.join('\n') + '\n' :
                catalog.text.replace(', version.ref = "reactor"', '')
            run ':consumer:verifyLocalOverride', '--offline', '--configuration-cache'
        }

        then:
        if (removal) {
            outputContains 'Local BOM override verified'
            assert file('consumer/build/bom-graph.json').text == beforeRemoval
        }

        where:
        localVersion | notation | includeBom | enabled | application | localPin | removal
        '1.0'        | 'string' | true       | true    | false       | true     | null
        '5.0'        | 'string' | true       | true    | false       | true     | null
        '1.0'        | 'enum'   | true       | true    | false       | true     | null
        '1.0'        | 'string' | false      | true    | false       | true     | null
        '1.0'        | 'string' | true       | false   | false       | true     | null
        '1.0'        | 'string' | true       | true    | true        | true     | null
        '4.0'        | 'string' | true       | true    | false       | false    | null
        '1.0'        | 'string' | null       | true    | false       | true     | null
        '1.0'        | 'enum'   | null       | true    | false       | true     | null
        '4.0'        | 'string' | true       | true    | false       | true     | 'version'
        '4.0'        | 'string' | true       | true    | false       | true     | 'declaration'
        '4.0'        | 'enum'   | true       | true    | false       | true     | 'version'
        '4.0'        | 'enum'   | true       | true    | false       | true     | 'declaration'
    }

    void 'known BOM #notation can use a platform property without a local catalog file'() {
        given:
        setupProject('', 'mnReactor')
        file('gradle/libs.versions.toml').delete()
        file('gradle').delete()
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example', false)", "importMicronautCatalog($argument)")

        when:
        run 'verifyCatalog', '--offline', '-PmicronautPlatformVersion=3.0'

        then:
        outputContains 'Catalog marker: 4.0'

        where:
        notation | argument
        'enum'   | 'io.micronaut.build.MicronautBom.REACTOR'
        'string' | '"micronaut-reactor"'
    }

    void 'Data string imports work after all their local BOM declarations are removed'() {
        given:
        setupProject('''
            [versions]
            micronaut = "5.0"
            micronaut-platform = "3.0"
            micronaut-test = "1.0"
            micronaut-logging = "1.0"
        ''')
        publishCatalog('io.micronaut.test:micronaut-test-bom', '1.0', '[libraries]\nmarker = "example:marker:1.0"')
        publishCatalog('io.micronaut.logging:micronaut-logging-bom', '1.0', '[libraries]\nmarker = "example:marker:1.0"')
        def imports = [
            'micronaut-reactor': ['io.micronaut.reactor:micronaut-reactor-bom', 'mnReactor'],
            'micronaut-rxjava3': ['io.micronaut.rxjava3:micronaut-rxjava3-bom', 'mnRxjava3'],
            'micronaut-r2dbc': ['io.micronaut.r2dbc:micronaut-r2dbc-bom', 'mnR2dbc'],
            'micronaut-spring': ['io.micronaut.spring:micronaut-spring-bom', 'mnSpring'],
            'micronaut-serde': ['io.micronaut.serde:micronaut-serde-bom', 'mnSerde'],
            'micronaut-sql': ['io.micronaut.sql:micronaut-sql-bom', 'mnSql'],
            'micronaut-kotlin': ['io.micronaut.kotlin:micronaut-kotlin-bom', 'mnKotlin'],
            'micronaut-multitenancy': ['io.micronaut.multitenancy:micronaut-multitenancy-bom', 'mnMultitenancy'],
            'micronaut-testresources': ['io.micronaut.testresources:micronaut-test-resources-bom', 'mnTestResources'],
            'micronaut-validation': ['io.micronaut.validation:micronaut-validation-bom', 'mnValidation'],
            'micronaut-mongo': ['io.micronaut.mongodb:micronaut-mongo-bom', 'mnMongo'],
            'micronaut-flyway': ['io.micronaut.flyway:micronaut-flyway-bom', 'mnFlyway'],
            'micronaut-coherence': ['io.micronaut.coherence:micronaut-coherence-bom', 'mnCoherence'],
            'micronaut-sourcegen': ['io.micronaut.sourcegen:micronaut-sourcegen-bom', 'mnSourcegen']
        ]
        imports.eachWithIndex { alias, entry, index ->
            publishCatalog(entry[0], '4.0', '[libraries]\nmarker = "example:marker:4.0"')
            file('repo/io/micronaut/platform/micronaut-platform/3.0/micronaut-platform-3.0.toml') << """
                unrelated-$index = { module = "${entry[0]}", version = "4.0" }
            """
        }
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example', false)",
            'importMicronautCatalog()\n' + imports.keySet().collect { "importMicronautCatalog('$it', true)" }.join('\n'))
        buildFile.text = """
            plugins { id 'java-library' }
            def catalogs = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension)
            def versions = ${imports.values().collect { it[1] }.inspect()}.collectEntries { name ->
                [name, catalogs.named(name).findLibrary('marker').get().get().versionConstraint.requiredVersion]
            }
            def modules = configurations.globalBoms.allDependencies.collect { it.group + ':' + it.name }
            assert catalogs.find('mn').present
            tasks.register('verifyCatalog') {
                inputs.property('versions', versions)
                inputs.property('modules', modules)
                doLast {
                    assert inputs.properties.versions.values().every { it == '4.0' }
                    assert inputs.properties.modules.containsAll(${imports.values().collect { it[0] }.inspect()})
                    println 'All 14 Data catalogs and BOMs imported without local declarations'
                }
            }
        """

        when:
        run 'verifyCatalog', '--offline', '--configuration-cache'

        then:
        outputContains 'All 14 Data catalogs and BOMs imported without local declarations'

        when:
        run 'verifyCatalog', '--offline', '--configuration-cache'

        then:
        outputContains 'Reusing configuration cache.'
    }

    void 'a known string alias keeps its local #kind override ahead of the platform'() {
        given:
        setupProject("""
            [versions]
            micronaut-platform = "3.0"
            [libraries]
            micronaut-reactor = { module = "$PLATFORM_MODULE", $declaration }
        """, 'mnReactor')
        publishCatalog(PLATFORM_MODULE, '1.0', '[libraries]\nmarker = "example:marker:1.0"')
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example', false)",
            "importMicronautCatalog('micronaut-reactor')")

        when:
        run 'verifyCatalog', '--offline'

        then:
        outputContains 'Catalog marker: 1.0'

        where:
        kind     | declaration
        'plain'  | 'version = "1.0"'
        'rich'   | 'version = { require = "1.0", strictly = "1.0", reject = ["4.0"] }'
    }

    void 'known string aliases can explicitly remain catalog-only and fall back to core'() {
        given:
        setupProject('''
            [versions]
            micronaut = "5.0"
        ''', 'mnSql')
        publishCatalog('io.micronaut.sql:micronaut-sql-bom', '2.0', '[libraries]\nmarker = "example:marker:2.0"')
        file('repo/io/micronaut/micronaut-core-bom/5.0/micronaut-core-bom-5.0.toml') << '''
            sql = { module = "io.micronaut.sql:micronaut-sql-bom", version = "2.0" }
        '''
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example', false)",
            "importMicronautCatalog('micronaut-sql', false)")
        buildFile << "\nassert configurations.findByName('globalBoms') == null\n"

        when:
        run 'verifyCatalog', '--offline'

        then:
        outputContains 'Catalog marker: 2.0'
    }

    void 'known BOM #bom imports #catalogName without a local library declaration'() {
        given:
        setupProject('''
            [versions]
            micronaut-platform = "3.0"
        ''', catalogName)
        publishCatalog(module, '4.0', '[libraries]\nmarker = "example:marker:4.0"')
        file('repo/io/micronaut/platform/micronaut-platform/3.0/micronaut-platform-3.0.toml') << """
            arbitrary-alias = { module = "$module", version = "4.0" }
        """
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example', false)",
            "importMicronautCatalog(io.micronaut.build.MicronautBom.$bom$argument)")
        buildFile << """
            assert configurations.globalBoms.allDependencies.any {
                it.group + ':' + it.name == '$module'
            } == ${argument != ', false'}
        """

        when:
        run 'verifyCatalog', '--offline', '--configuration-cache'

        then:
        outputContains 'Catalog marker: 4.0'

        when:
        run 'verifyCatalog', '--offline', '--configuration-cache'

        then:
        outputContains 'Reusing configuration cache.'

        where:
        bom              | module                                                     | catalogName       | argument
        'REACTOR'        | PLATFORM_MODULE                                            | 'mnReactor'       | ''
        'MONGO'          | 'io.micronaut.mongodb:micronaut-mongo-bom'                   | 'mnMongo'         | ', false'
        'SERDE'          | 'io.micronaut.serde:micronaut-serde-bom'                     | 'mnSerde'         | ''
        'TEST_RESOURCES' | 'io.micronaut.testresources:micronaut-test-resources-bom'    | 'mnTestResources' | ''
    }

    void 'known BOM resolves from core when the platform does not manage it'() {
        given:
        setupProject('''
            [versions]
            micronaut = "5.0"
            micronaut-platform = "3.0"
        ''', 'mnSql')
        publishCatalog('io.micronaut.sql:micronaut-sql-bom', '2.0', '[libraries]\nmarker = "example:marker:2.0"')
        file('repo/io/micronaut/micronaut-core-bom/5.0/micronaut-core-bom-5.0.toml') << '''
            unrelated-alias = { module = "io.micronaut.sql:micronaut-sql-bom", version = "2.0" }
        '''
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example', false)",
            'importMicronautCatalog(io.micronaut.build.MicronautBom.SQL)')

        when:
        run 'verifyCatalog', '--offline'

        then:
        outputContains 'Catalog marker: 2.0'
    }

    void 'known BOM respects a local #kind override and optionally applies constraints'() {
        given:
        setupProject("""
            [versions]
            micronaut = "5.0"
            micronaut-platform = "3.0"
            reactor = $version
            [libraries]
            reactor = { module = "$PLATFORM_MODULE", $declaration }
        """, 'mnReactor')
        publishCatalog(PLATFORM_MODULE, '5.0', '[libraries]\nmarker = "example:marker:5.0"', true)
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example', false)",
            'importMicronautCatalog(io.micronaut.build.MicronautBom.REACTOR, true)')
        setupBomConsumer(true, true, '5.0', PLATFORM_MODULE)

        when:
        run 'verifyCatalog', ':consumer:verifyBom', '--offline', '--configuration-cache'

        then:
        outputContains 'Catalog marker: 5.0'
        outputContains 'BOM included: true'

        when:
        run 'verifyCatalog', ':consumer:verifyBom', '--offline', '--configuration-cache'

        then:
        outputContains 'Reusing configuration cache.'

        where:
        kind        | version                                               | declaration
        'inline'    | '"5.0"'                                               | 'version = "5.0"'
        'reference' | '"5.0"'                                               | 'version.ref = "reactor"'
        'rich'      | '{ require = "5.0", strictly = "5.0", reject = ["4.0"] }' | 'version.ref = "reactor"'
    }

    void 'known BOM missing from both catalogs fails instead of guessing a version'() {
        given:
        setupProject('''
            [versions]
            micronaut = "5.0"
            micronaut-platform = "3.0"
        ''')
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example', false)",
            'importMicronautCatalog(io.micronaut.build.MicronautBom.SQL)')

        when:
        fails 'help', '--offline'

        then:
        errorOutputContains 'do not supply a catalog version for io.micronaut.sql:micronaut-sql-bom'
    }

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
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example', false)", "importMicronautCatalog('example'$argument)")
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
        'enabled by default'         | ''        | true      | true
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
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example', false)", "importMicronautCatalog('example', true)")
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
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example', false)", "importMicronautCatalog('example', true)")
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
            settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example', false)", "importMicronautCatalog('example', true)")
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

    void 'unconfigured platform cannot be imported catalog-only'() {
        given:
        setupProject('''
            [libraries]
            example = { module = "io.micronaut.platform:micronaut-platform", version = "3.0" }
        ''', 'mnPlatform')
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example', false)", "importMicronautCatalog('example', false)")

        when:
        fails 'help', '--offline'

        then:
        errorOutputContains 'The platform must be imported using importMicronautCatalog(alias, true).'

    }

    void 'unconfigured platform is included by a one-argument import'() {
        given:
        setupProject('''
            [versions]
            micronaut = "5.0"
            [libraries]
            example = { module = "io.micronaut.platform:micronaut-platform", version = "3.0" }
        ''', 'mnPlatform')
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example', false)", "importMicronautCatalog('example')")
        setupBomConsumer(true, true, '3.0', 'io.micronaut.platform:micronaut-platform')

        when:
        run 'verifyCatalog', ':consumer:verifyBom', '--offline', '--configuration-cache'

        then:
        outputContains 'Catalog marker: 3.0'
        outputContains 'BOM included: true'
    }

    void 'included BOMs also reach the existing application BOM bucket'() {
        given:
        setupProject("""
            [versions]
            micronaut = "5.0"
            [libraries]
            example = { module = "$MODULE", version = "1.0" }
        """)
        settingsFile.text = settingsFile.text.replace("importMicronautCatalog('example', false)", "importMicronautCatalog('example', true)")
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
            different-core-name = { module = "io.micronaut:micronaut-core-bom", version = "5.0" }
            different-test-name = { module = "io.micronaut.test:micronaut-test-bom", version = "1.0" }
            different-logging-name = { module = "io.micronaut.logging:micronaut-logging-bom", version = "1.0" }
        """)
        publishCatalog('io.micronaut.test:micronaut-test-bom', '1.0', '[libraries]\nmarker = "example:marker:1.0"')
        publishCatalog('io.micronaut.logging:micronaut-logging-bom', '1.0', '[libraries]\nmarker = "example:marker:1.0"')
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
            micronautBuild { importMicronautCatalog('example', false) }
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

    private void publishCatalog(String module, String version, String content, boolean managesExample = false) {
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
                dependencyConstraints: managesExample || module in [MODULE, 'io.micronaut.platform:micronaut-platform'] ?
                    [[group: 'example', module: 'managed', version: [requires: version]]] : []
            ] }
        ])
    }
}

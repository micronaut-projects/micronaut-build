package io.micronaut.build.graalvm

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class GenerateGraalVmReachabilityMetadataTaskTest extends Specification {
    private Project project
    private GenerateGraalVmReachabilityMetadataTask task
    private File generatedOutputFile

    def setup() {
        project = ProjectBuilder.builder().build()
        generatedOutputFile = project.layout.buildDirectory.file("metadata/library-and-framework-list.json").get().asFile
        task = project.tasks.create("generateReachabilityMetadata", GenerateGraalVmReachabilityMetadataTask) {
            minimumVersion.set("4.8.0")
            metadataLocations.set([])
            testsLocations.set(["https://github.com/micronaut-projects/micronaut-test/actions/workflows/graalvm.yml"])
            testLevel.set("community-tested")
            moduleDescriptions.put("io.micronaut.test:micronaut-test-core", "Micronaut Test Core")
            moduleDescriptions.put("io.micronaut.test:micronaut-test-junit5", "Micronaut Test JUnit 5")
        }
        task.outputFile.set(generatedOutputFile)
    }

    def "generates library and framework list entries"() {
        when:
        task.generate()

        then:
        generatedOutputFile.text == """[
  {
    "artifact": "io.micronaut.test:micronaut-test-core",
    "description": "Micronaut Test Core",
    "details": [
      {
        "minimum_version": "4.8.0",
        "metadata_locations": [],
        "tests_locations": ["https://github.com/micronaut-projects/micronaut-test/actions/workflows/graalvm.yml"],
        "test_level": "community-tested"
      }
    ]
  },
  {
    "artifact": "io.micronaut.test:micronaut-test-junit5",
    "description": "Micronaut Test JUnit 5",
    "details": [
      {
        "minimum_version": "4.8.0",
        "metadata_locations": [],
        "tests_locations": ["https://github.com/micronaut-projects/micronaut-test/actions/workflows/graalvm.yml"],
        "test_level": "community-tested"
      }
    ]
  }
]
"""
    }

    def "escapes json strings"() {
        given:
        task.moduleDescriptions.empty()
        task.moduleDescriptions.put("io.micronaut.test:micronaut-json", "Line one\n\"quoted\"")

        when:
        task.generate()

        then:
        generatedOutputFile.text.contains('"description": "Line one\\n\\"quoted\\""')
    }
}

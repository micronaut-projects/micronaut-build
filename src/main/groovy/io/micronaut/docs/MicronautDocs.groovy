package io.micronaut.docs

import groovy.transform.CompileStatic

@CompileStatic
class MicronautDocs {
    static String GITHUB_ORG = 'micronaut-projects'
    static String GITHUB_REPO = 'micronaut-docs'
    static String GIT_BRANCH = 'master'
    static final String RESOURCES_FOLDER = 'src/main/docs/resources'
    static final String GITHUBRAW = "https://raw.githubusercontent.com/${GITHUB_ORG}/${GITHUB_REPO}/${GIT_BRANCH}/${RESOURCES_FOLDER}"
}

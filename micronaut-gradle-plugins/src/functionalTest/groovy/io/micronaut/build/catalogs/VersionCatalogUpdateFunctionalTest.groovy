/*
 * Copyright 2003-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.build.catalogs

import io.micronaut.build.AbstractFunctionalTest
import software.xdev.mockserver.client.MockServerClient
import software.xdev.mockserver.mock.action.ExpectationResponseCallback
import software.xdev.mockserver.model.HttpRequest
import software.xdev.mockserver.model.HttpResponse
import software.xdev.mockserver.model.MediaType
import software.xdev.mockserver.netty.MockServer
import spock.lang.Shared

import static software.xdev.mockserver.model.HttpRequest.request
import static software.xdev.mockserver.model.HttpResponse.notFoundResponse
import static software.xdev.mockserver.model.HttpResponse.response

class VersionCatalogUpdateFunctionalTest extends AbstractFunctionalTest {

    @Shared
    private MockServer mockServer
    @Shared
    private MockServerClient repository

    def setup() {
        mockServer = new MockServer()
        repository = new MockServerClient("localhost", mockServer.localPort)
        buildFile << """
            plugins {
                id 'io.micronaut.build.internal.version-catalog-updates'            
            }
            
            repositories {
                maven {
                    url "http://localhost:${mockServer.localPort}"
                    allowInsecureProtocol = true
                }        
            }
        """
    }

    def cleanup() {
        repository.close()
        mockServer.close()
    }

    def "can update a version catalog"() {
        debug = true
        def catalogFile = file("gradle/libs.versions.toml")
        catalogFile.text = VersionCatalogUpdateFunctionalTest.getResourceAsStream("${VersionCatalogUpdateFunctionalTest.simpleName}/initial-${idx}.versions.toml").text

        when:
        repository.when(
                request()
        ).respond(new LoggingCallback())
        if (idx == 7) {
            buildFile << """
                tasks.named("updateVersionCatalogs") {
                    rejectedVersionsPerModule['awesome.lib:awesome'] = '3\\\\.0\\\\.[8-9]'
                }
            """
        }
        run 'useLatestVersions'

        then:
        tasks {
            succeeded ':updateVersionCatalogs', ':useLatestVersions'
        }

        file("gradle").eachFileRecurse {
            if (it.file) {
                println it
                println it.text
            }
        }

        def expected = VersionCatalogUpdateFunctionalTest.getResourceAsStream("${VersionCatalogUpdateFunctionalTest.simpleName}/updated-${idx}.versions.toml").text

        catalogFile.text == expected

        where:
        idx << (0..7)
    }

    static class LoggingCallback implements ExpectationResponseCallback {

        @Override
        HttpResponse handle(HttpRequest httpRequest) throws Exception {
            String path = "/repository${httpRequest.path}"
            println "Requesting $path"
            def body = VersionCatalogUpdateFunctionalTest.getResourceAsStream(path)
            if (body) {
                return response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_XML)
                        .withBody(body.bytes)
            } else {
                println "Not found"
                notFoundResponse()
            }
        }
    }
}

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
import org.mockserver.configuration.Configuration
import org.mockserver.integration.ClientAndServer
import org.mockserver.logging.MockServerLogger
import org.mockserver.mock.action.ExpectationResponseCallback
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.model.MediaType
import org.mockserver.socket.tls.KeyStoreFactory
import spock.lang.Shared

import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

import static org.mockserver.integration.ClientAndServer.startClientAndServer
import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.notFoundResponse
import static org.mockserver.model.HttpResponse.response

class VersionCatalogUpdateFunctionalTest extends AbstractFunctionalTest {

    ClientAndServer repository

    @Shared
    private SSLSocketFactory sslFactory

    def setupSpec() {
        sslFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
        HttpsURLConnection.setDefaultSSLSocketFactory(new KeyStoreFactory(new Configuration(), new MockServerLogger()).sslContext().socketFactory)
    }

    def cleanupSpec() {
        HttpsURLConnection.setDefaultSSLSocketFactory(sslFactory)
    }

    def setup() {
        repository = startClientAndServer()
        HttpsURLConnection.setDefaultSSLSocketFactory(new KeyStoreFactory(new Configuration(), new MockServerLogger()).sslContext().socketFactory)
        buildFile << """
            plugins {
                id 'io.micronaut.build.internal.version-catalog-updates'            
            }
            
            repositories {
                maven {
                    url "https://localhost:${repository.port}"
                }        
            }
        """
    }

    def cleanup() {
        repository.stop()
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

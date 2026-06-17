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

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.micronaut.build.AbstractFunctionalTest
import spock.lang.Shared

import java.net.InetSocketAddress

class VersionCatalogUpdateFunctionalTest extends AbstractFunctionalTest {

    @Shared
    private HttpServer repository

    def setup() {
        repository = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
        repository.createContext("/", VersionCatalogUpdateFunctionalTest::serveRepositoryResource)
        repository.start()
        buildFile << """
            plugins {
                id 'io.micronaut.build.internal.version-catalog-updates'            
            }
            
            repositories {
                maven {
                    url "http://127.0.0.1:${repository.address.port}"
                    allowInsecureProtocol = true
                }        
            }
        """
    }

    def cleanup() {
        repository.stop(0)
    }

    def "can update a version catalog"() {
        debug = true
        def catalogFile = file("gradle/libs.versions.toml")
        catalogFile.text = VersionCatalogUpdateFunctionalTest.getResourceAsStream("${VersionCatalogUpdateFunctionalTest.simpleName}/initial-${idx}.versions.toml").text

        when:
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

    private static void serveRepositoryResource(HttpExchange exchange) throws IOException {
        String path = "/repository${exchange.requestURI.path}"
        println "Requesting $path"
        def body = VersionCatalogUpdateFunctionalTest.getResourceAsStream(path)
        try {
            if (body) {
                byte[] response = body.bytes
                exchange.responseHeaders.add("Content-Type", "application/xml")
                exchange.sendResponseHeaders(200, response.length)
                exchange.responseBody.write(response)
            } else {
                println "Not found"
                exchange.sendResponseHeaders(404, -1)
            }
        } finally {
            body?.close()
            exchange.close()
        }
    }
}

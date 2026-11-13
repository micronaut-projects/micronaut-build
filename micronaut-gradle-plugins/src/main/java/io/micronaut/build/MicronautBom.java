/*
 * Copyright 2003-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.build;

import java.util.Arrays;
import java.util.Optional;

/**
 * Known Micronaut BOM coordinates from the Micronaut platform 5.2.6 catalog.
 * Local declarations matching the BOM's Maven coordinates take priority.
 * Otherwise versions are resolved from the configured platform or core catalog,
 * never from this enum. Older platforms may not manage every BOM listed here.
 *
 * @see MicronautBuildSettingsExtension#importMicronautCatalog(MicronautBom)
 */
public enum MicronautBom {
    ACME("io.micronaut.acme", "micronaut-acme-bom"),
    AOT("io.micronaut.aot", "micronaut-aot-bom"),
    AWS("io.micronaut.aws", "micronaut-aws-bom"),
    AZURE("io.micronaut.azure", "micronaut-azure-bom"),
    CACHE("io.micronaut.cache", "micronaut-cache-bom"),
    CASSANDRA("io.micronaut.cassandra", "micronaut-cassandra-bom"),
    CHATBOTS("io.micronaut.chatbots", "micronaut-chatbots-bom"),
    COHERENCE("io.micronaut.coherence", "micronaut-coherence-bom"),
    CONTROL_PANEL("io.micronaut.controlpanel", "micronaut-control-panel-bom"),
    CORE("io.micronaut", "micronaut-core-bom"),
    CRAC("io.micronaut.crac", "micronaut-crac-bom"),
    DATA("io.micronaut.data", "micronaut-data-bom"),
    DISCOVERY_CLIENT("io.micronaut.discovery", "micronaut-discovery-client-bom"),
    ECLIPSESTORE("io.micronaut.eclipsestore", "micronaut-eclipsestore-bom"),
    ELASTICSEARCH("io.micronaut.elasticsearch", "micronaut-elasticsearch-bom"),
    EMAIL("io.micronaut.email", "micronaut-email-bom"),
    FLYWAY("io.micronaut.flyway", "micronaut-flyway-bom"),
    GCP("io.micronaut.gcp", "micronaut-gcp-bom"),
    GRAAL_LANGUAGES("io.micronaut.graal-languages", "micronaut-graal-languages-bom"),
    GRAPHQL("io.micronaut.graphql", "micronaut-graphql-bom"),
    GROOVY("io.micronaut.groovy", "micronaut-groovy-bom"),
    GRPC("io.micronaut.grpc", "micronaut-grpc-bom"),
    GUICE("io.micronaut.guice", "micronaut-guice-bom"),
    GUIDES("io.micronaut.guides", "micronaut-guides-bom"),
    HIBERNATE_VALIDATOR("io.micronaut.beanvalidation", "micronaut-hibernate-validator-bom"),
    JACKSON_XML("io.micronaut.xml", "micronaut-jackson-xml-bom"),
    JAKARTA_EL("io.micronaut.el", "micronaut-jakarta-el-bom"),
    JAXRS("io.micronaut.jaxrs", "micronaut-jaxrs-bom"),
    JMS("io.micronaut.jms", "micronaut-jms-bom"),
    JMX("io.micronaut.jmx", "micronaut-jmx-bom"),
    JSON_SCHEMA("io.micronaut.jsonschema", "micronaut-json-schema-bom"),
    KAFKA("io.micronaut.kafka", "micronaut-kafka-bom"),
    KOTLIN("io.micronaut.kotlin", "micronaut-kotlin-bom"),
    KUBERNETES("io.micronaut.kubernetes", "micronaut-kubernetes-bom"),
    LANGCHAIN4J("io.micronaut.langchain4j", "micronaut-langchain4j-bom"),
    LIQUIBASE("io.micronaut.liquibase", "micronaut-liquibase-bom"),
    LOGGING("io.micronaut.logging", "micronaut-logging-bom"),
    MCP("io.micronaut.mcp", "micronaut-mcp-bom"),
    MICROMETER("io.micronaut.micrometer", "micronaut-micrometer-bom"),
    MONGO("io.micronaut.mongodb", "micronaut-mongo-bom"),
    MQTT("io.micronaut.mqtt", "micronaut-mqtt-bom"),
    MULTITENANCY("io.micronaut.multitenancy", "micronaut-multitenancy-bom"),
    NATS("io.micronaut.nats", "micronaut-nats-bom"),
    NEO4J("io.micronaut.neo4j", "micronaut-neo4j-bom"),
    OBJECT_STORAGE("io.micronaut.objectstorage", "micronaut-object-storage-bom"),
    OPENAPI("io.micronaut.openapi", "micronaut-openapi-bom"),
    OPENREWRITE("io.micronaut.openrewrite", "micronaut-openrewrite-bom"),
    OPENSEARCH("io.micronaut.opensearch", "micronaut-opensearch-bom"),
    ORACLECLOUD("io.micronaut.oraclecloud", "micronaut-oraclecloud-bom"),
    PICOCLI("io.micronaut.picocli", "micronaut-picocli-bom"),
    PROBLEM_JSON("io.micronaut.problem", "micronaut-problem-json-bom"),
    PROJECTGEN("io.micronaut.projectgen", "micronaut-projectgen-bom"),
    PULSAR("io.micronaut.pulsar", "micronaut-pulsar-bom"),
    R2DBC("io.micronaut.r2dbc", "micronaut-r2dbc-bom"),
    RABBITMQ("io.micronaut.rabbitmq", "micronaut-rabbitmq-bom"),
    REACTOR("io.micronaut.reactor", "micronaut-reactor-bom"),
    REDIS("io.micronaut.redis", "micronaut-redis-bom"),
    RSS("io.micronaut.rss", "micronaut-rss-bom"),
    RXJAVA3("io.micronaut.rxjava3", "micronaut-rxjava3-bom"),
    SECURITY("io.micronaut.security", "micronaut-security-bom"),
    SERDE("io.micronaut.serde", "micronaut-serde-bom"),
    SERVLET("io.micronaut.servlet", "micronaut-servlet-bom"),
    SESSION("io.micronaut.session", "micronaut-session-bom"),
    SOURCEGEN("io.micronaut.sourcegen", "micronaut-sourcegen-bom"),
    SPRING("io.micronaut.spring", "micronaut-spring-bom"),
    SQL("io.micronaut.sql", "micronaut-sql-bom"),
    TEST("io.micronaut.test", "micronaut-test-bom"),
    TEST_RESOURCES("io.micronaut.testresources", "micronaut-test-resources-bom"),
    TOML("io.micronaut.toml", "micronaut-toml-bom"),
    TRACING("io.micronaut.tracing", "micronaut-tracing-bom"),
    VALIDATION("io.micronaut.validation", "micronaut-validation-bom"),
    VIEWS("io.micronaut.views", "micronaut-views-bom");

    private final String groupId;
    private final String artifactId;

    MicronautBom(String groupId, String artifactId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    /**
     * @return the BOM's Maven group ID
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * @return the BOM's Maven artifact ID
     */
    public String getArtifactId() {
        return artifactId;
    }

    static Optional<MicronautBom> findByAlias(String alias) {
        return Arrays.stream(values()).filter(bom ->
            bom.artifactId.equals(alias + "-bom") ||
                (bom == TEST_RESOURCES && "micronaut-testresources".equals(alias))
        ).findFirst();
    }
}

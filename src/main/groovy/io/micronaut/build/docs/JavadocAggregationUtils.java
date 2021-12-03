/*
 * Copyright 2003-2021 the original author or authors.
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
package io.micronaut.build.docs;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.model.ObjectFactory;

abstract class JavadocAggregationUtils {
    public static final String AGGREGATED_JAVADOC_PARTICIPANT_SOURCES = "aggregatedJavadocParticipantSources";
    public static final String AGGREGATED_JAVADOC_PARTICIPANT_DEPS = "aggregatedJavadocParticipantDependencies";

    static void configureJavadocSourcesAggregationAttributes(ObjectFactory objects, Configuration conf) {
        conf.attributes(attrs -> {
            attrs.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, AGGREGATED_JAVADOC_PARTICIPANT_SOURCES));
            attrs.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.DOCUMENTATION));
        });
    }

}

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
package io.micronaut.build;


import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

public abstract class DeprecatedTask extends DefaultTask {
    @Input
    public abstract Property<String> getMessage();

    @Optional
    @Input
    public abstract Property<String> getReplacement();

    @TaskAction
    public void logDeprecation() {
        String message = getMessage().get();
        String replacement = getReplacement().getOrNull();
        if (replacement != null) {
            message += ". Use " + replacement + " instead.";
        }
        getLogger().warn(message);
    }
}

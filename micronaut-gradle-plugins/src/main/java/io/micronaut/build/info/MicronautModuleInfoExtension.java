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
package io.micronaut.build.info;

import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

/**
 * This extension can be used to configure the project
 * Micronaut Module Info descriptor. That descriptor
 * is used at runtime to provide information about Micronaut
 * modules which are found on classpath.
 *
 * All parameters are configured with reasonable
 * defaults, but you may want to override the parent
 * module id, for example, if a project wants to expose
 * some modules as children of another module.
 *
 */
public interface MicronautModuleInfoExtension {
    /**
     * Determines if module descriptor generation is enabled.
     * @return the enabled property
     */
    Property<Boolean> getEnabled();

    /**
     * The package name of the descriptor. By default, it is
     * derived from the group id: groupid + .info
     * @return the package name
     */
    Property<String> getPackageName();

    /**
     * The class name of the generated descriptor. By default,
     * derived from the project name.
     * @return the class name
     */
    Property<String> getClassName();

    /**
     * The human readable name of this module. Since we
     * don't have such a thing now, it currently defaults
     * to the project name.
     * @return the module name
     */
    Property<String> getModuleName();

    /**
     * The module version.
     * @return The module version
     */
    Property<String> getModuleVersion();

    /**
     * The description of the module. By default uses the project's
     * description.
     * @return the module description
     */
    Property<String> getModuleDescription();

    /**
     * The project group id. Shouldn't be changed unless you know
     * what you are doing.
     * @return the group id
     */
    Property<String> getGroupId();

    /**
     * The project artifact id. Should't be changed unless you know
     * what you are doing.
     * @return the artifact id
     */
    Property<String> getArtifactId();

    /**
     * The parent module id. By default, defauts to the project
     * of the multi-project build which is the "-core" module.
     * It is possible to create a deeper hierarchy. The id must
     * consist of the group id:artifact id
     * @return the parent module id
     */
    Property<String> getParentModuleId();

    /**
     * Tags for the module. For now purely informative, may be
     * used for filtering.
     * @return the module tags
     */
    SetProperty<String> getTags();
}

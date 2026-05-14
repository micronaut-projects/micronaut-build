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
package io.micronaut.docs;

import java.io.File;
import java.util.Properties;

public interface GuidePublisher {

    void setFileOperations(DocFileOperations fileOperations);

    void setAsciidoc(boolean asciidoc);

    void setDocResources(File docResources);

    void setApiDir(File apiDir);

    void setLanguage(String language);

    void setSourceRepo(String sourceRepo);

    void setImages(File images);

    void setCss(File css);

    void setFonts(File fonts);

    void setJs(File js);

    void setStyle(File style);

    void setVersion(String version);

    void setEngineProperties(Properties properties);

    void registerHiddenMacro();

    void publish();
}

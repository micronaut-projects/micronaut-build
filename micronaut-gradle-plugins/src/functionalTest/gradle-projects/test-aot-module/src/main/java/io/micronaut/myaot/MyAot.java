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
package io.micronaut.myaot;

import com.squareup.javapoet.MethodSpec;
import io.micronaut.aot.core.AOTContext;
import io.micronaut.aot.core.AOTModule;
import io.micronaut.aot.core.codegen.AbstractCodeGenerator;

import javax.lang.model.element.Modifier;

import static io.micronaut.myaot.MyAot.DESCRIPTION;
import static io.micronaut.myaot.MyAot.ID;

@AOTModule(
        id = ID,
        description = DESCRIPTION
)
public class MyAot extends AbstractCodeGenerator {
    public static final String ID = "myaot";
    public static final String DESCRIPTION = "My AOT module";

    @Override
    public void generate(AOTContext context) {
        context.registerStaticInitializer(
                MethodSpec.methodBuilder("foo")
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("System.out.println(\"Hello World\")")
                        .build());
    }
}

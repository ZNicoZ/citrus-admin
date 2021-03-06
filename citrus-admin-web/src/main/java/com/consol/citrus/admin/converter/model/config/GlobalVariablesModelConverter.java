/*
 * Copyright 2006-2017 the original author or authors.
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

package com.consol.citrus.admin.converter.model.config;

import com.consol.citrus.admin.converter.model.AbstractModelConverter;
import com.consol.citrus.model.config.core.GlobalVariablesModel;
import com.consol.citrus.variable.GlobalVariables;
import org.springframework.stereotype.Component;

import java.util.AbstractMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Christoph Deppisch
 */
@Component
public class GlobalVariablesModelConverter extends AbstractModelConverter<GlobalVariablesModel, GlobalVariables> {
    /**
     * Default constructor.
     */
    public GlobalVariablesModelConverter() {
        super(GlobalVariablesModel.class, GlobalVariables.class);

        addDecorator(new MethodCallDecorator("setFiles") {
            @Override
            public boolean allowMethodCall(Object arg) {
                return false;
            }
        });

        addDecorator(new MethodCallDecorator("setVariables") {
            @Override
            public Object decorateArgument(Object arg) {
                getAdditionalImports().add(AbstractMap.class);
                getAdditionalImports().add(Collectors.class);
                getAdditionalImports().add(Stream.class);

                List<GlobalVariablesModel.Variable> variables = (List<GlobalVariablesModel.Variable>) arg;
                StringBuilder codeBuilder = new StringBuilder();

                codeBuilder.append("Stream.of(");
                variables.forEach(variable -> codeBuilder.append(String.format("%n\t\t\t\tnew AbstractMap.SimpleEntry<String, Object>(\"%s\", \"%s\"),", variable.getName(), variable.getValue())));
                codeBuilder.deleteCharAt(codeBuilder.length() - 1);
                codeBuilder.append(String.format(")%n\t\t\t.collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue))"));

                return codeBuilder.toString();
            }
        });
    }

    @Override
    public GlobalVariablesModel convert(GlobalVariables model) {
        GlobalVariablesModel converted = super.convert(model);

        model.getVariables().forEach((key, value) -> {
            GlobalVariablesModel.Variable variable = new GlobalVariablesModel.Variable();
            variable.setName(key);
            variable.setValue(value.toString());
            converted.getVariables().add(variable);
        });

        return converted;
    }

    @Override
    public String getJavaConfig(GlobalVariablesModel model) {
        return getJavaConfig(model, "globalVariables");
    }
}

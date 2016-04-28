/*
 * Copyright 2006-2016 the original author or authors.
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

package com.consol.citrus.admin.service;

import com.consol.citrus.admin.converter.action.ActionConverter;
import com.consol.citrus.admin.converter.action.TestActionConverter;
import com.consol.citrus.admin.exception.ApplicationRuntimeException;
import com.consol.citrus.admin.marshal.XmlTestMarshaller;
import com.consol.citrus.admin.model.*;
import com.consol.citrus.admin.model.spring.SpringBeans;
import com.consol.citrus.model.testcase.core.TestcaseDefinition;
import com.consol.citrus.model.testcase.core.VariablesDefinition;
import com.consol.citrus.util.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.xml.transform.StringSource;

import javax.xml.bind.annotation.XmlRootElement;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Christoph Deppisch
 */
@Service
public class TestCaseService {

    /** Logger */
    private static Logger log = LoggerFactory.getLogger(TestCaseService.class);

    @Autowired
    private List<TestActionConverter<?, ? extends com.consol.citrus.TestAction>> actionConverter;

    /**
     * Lists all available Citrus test cases grouped in test packages.
     * @param project
     * @return
     */
    public List<TestPackage> getTestPackages(Project project) {
        Map<String, TestPackage> testPackages = new HashMap<>();
        List<Test> tests = new ArrayList<>();

        List<File> sourceFiles = FileUtils.findFiles(getJavaDirectory(project), StringUtils.commaDelimitedListToSet(project.getSettings().getJavaFilePattern()));
        for (File sourceFile : sourceFiles) {
            String className = FilenameUtils.getBaseName(sourceFile.getName());
            String testPackageName = sourceFile.getPath().substring(getJavaDirectory(project).length(), sourceFile.getPath().length() - sourceFile.getName().length())
                    .replace(File.separatorChar, '.');

            if (testPackageName.endsWith(".")) {
                testPackageName = testPackageName.substring(0, testPackageName.length() - 1);
            }

            tests.addAll(findTests(sourceFile, testPackageName, className));
        }

        for (Test test : tests) {
            if (!testPackages.containsKey(test.getPackageName())) {
                TestPackage testPackage = new TestPackage();
                testPackage.setName(test.getPackageName());
                testPackages.put(test.getPackageName(), testPackage);
            }

            testPackages.get(test.getPackageName()).getTests().add(test);
        }

        return Arrays.asList(testPackages.values().toArray(new TestPackage[testPackages.size()]));
    }

    /**
     * Find all tests in give source file.
     * @param sourceFile
     * @param packageName
     * @param className
     * @return
     */
    private List<Test> findTests(File sourceFile, String packageName, String className) {
        List<Test> tests = new ArrayList<>();

        try {
            String sourceCode = FileUtils.readToString(new FileSystemResource(sourceFile));

            Matcher matcher = Pattern.compile("@CitrusTest").matcher(sourceCode);
            while (matcher.find()) {
                Test test = new Test();
                test.setType(TestType.JAVA);
                test.setClassName(className);
                test.setPackageName(packageName);

                String snippet = StringUtils.trimAllWhitespace(sourceCode.substring(matcher.start(), sourceCode.indexOf('{', matcher.start())));
                String methodName = snippet.substring(snippet.indexOf("publicvoid") + 10);
                methodName = methodName.substring(0, methodName.indexOf("("));
                test.setMethodName(methodName);

                if (snippet.contains("@CitrusTest(name=")) {
                    String explicitName = snippet.substring(snippet.indexOf("name=\"") + 6);
                    explicitName = explicitName.substring(0, explicitName.indexOf("\""));
                    test.setName(explicitName);
                } else {
                    test.setName(className + "." + methodName);
                }

                tests.add(test);
            }

            matcher = Pattern.compile("@CitrusXmlTest").matcher(sourceCode);
            while (matcher.find()) {
                Test test = new Test();
                test.setType(TestType.XML);
                test.setClassName(className);
                test.setPackageName(packageName);

                String snippet = StringUtils.trimAllWhitespace(sourceCode.substring(matcher.start(), sourceCode.indexOf('{', matcher.start())));
                String methodName = snippet.substring(snippet.indexOf("publicvoid") + 10);
                methodName = methodName.substring(0, methodName.indexOf("("));
                test.setMethodName(methodName);

                if (snippet.contains("@CitrusXmlTest(name=")) {
                    String explicitName = snippet.substring(snippet.indexOf("name=\"") + 6);
                    explicitName = explicitName.substring(0, explicitName.indexOf("\""));
                    test.setName(explicitName);
                } else {
                    test.setName(methodName);
                }

                tests.add(test);
            }
        } catch (IOException e) {
            log.error("Failed to read test source file", e);
        }

        return tests;
    }

    /**
     * Gets test case details such as status, description, author.
     * @return
     */
    public TestDetail getTestDetail(Project project, String packageName, String className, String methodName, String testName, TestType type) {
        TestDetail testDetail = new TestDetail();
        testDetail.setName(testName);
        testDetail.setClassName(className);
        testDetail.setMethodName(methodName);
        testDetail.setPackageName(packageName);

        testDetail.setType(type);

        TestcaseDefinition testModel = getTestModel(project, testDetail);

        if (testModel.getVariables() != null) {
            for (VariablesDefinition.Variable variable : testModel.getVariables().getVariables()) {
                testDetail.getVariables().put(variable.getName(), variable.getValue());
            }
        }

        testDetail.setDescription(testModel.getDescription().trim().replaceAll(" +", " ").replaceAll("\t", ""));
        testDetail.setAuthor(testModel.getMetaInfo().getAuthor());
        testDetail.setLastModified(testModel.getMetaInfo().getLastUpdatedOn().getTimeInMillis());

        testDetail.setFile(getTestDirectory(project) + packageName.replace('.', File.separatorChar) + File.separator + FilenameUtils.getBaseName(testName));

        for (Object actionType : testModel.getActions().getActionsAndSendsAndReceives()) {
            TestAction model = null;
            for (TestActionConverter converter : actionConverter) {
                if (converter.getModelClass().isInstance(actionType)) {
                    model = converter.convert(actionType);
                    break;
                }
            }

            if (model == null) {
                model = new ActionConverter(actionType.getClass().getAnnotation(XmlRootElement.class).name()).convert(actionType);
            }

            testDetail.getActions().add(model);
        }

        return testDetail;
    }

    /**
     * Gets the source code for given test information.
     * @param project
     * @param packageName
     * @param className
     * @param methodName
     * @param testName
     * @param type
     * @return
     */
    public String getSourceCode(Project project, String packageName, String className, String methodName, String testName, TestType type) {
        TestDetail testDetail = new TestDetail();
        testDetail.setName(testName);
        testDetail.setClassName(className);
        testDetail.setMethodName(methodName);
        testDetail.setPackageName(packageName);

        testDetail.setType(type);

        return getSourceCode(project, testDetail);
    }

    /**
     * Gets the source code for the given test.
     * @param project
     * @param detail
     * @return
     */
    public String getSourceCode(Project project, TestDetail detail) {
        String sourceFilePath;
        if (detail.getType().equals(TestType.JAVA)) {
            sourceFilePath = getJavaDirectory(project) + detail.getPackageName().replace('.', File.separatorChar) + File.separator + detail.getClassName() + ".java";
        } else {
            sourceFilePath = getTestDirectory(project) + detail.getPackageName().replace('.', File.separatorChar) + File.separator + detail.getName() + ".xml";
        }

        try {
            if (new File(sourceFilePath).exists()) {
                return FileUtils.readToString(new FileInputStream(sourceFilePath));
            } else {
                log.warn("Unable to find source code for path: " + sourceFilePath);
                return "No sources available!";
            }
        } catch (IOException e) {
            throw new ApplicationRuntimeException("Failed to load test case source code", e);
        }
    }

    /**
     * Reads either XML or Java test definition to model class.
     * @param project
     * @return
     */
    private TestcaseDefinition getTestModel(Project project, TestDetail detail) {
        if (detail.getType().equals(TestType.XML)) {
            return getXmlTestModel(project, detail);
        } else if (detail.getType().equals(TestType.JAVA)) {
            return getJavaTestModel(project, detail);
        } else {
            throw new ApplicationRuntimeException("Unsupported test case type: " + detail.getType());
        }
    }

    /**
     * Get test case model from XML source code.
     * @param project
     * @param detail
     * @return
     */
    private TestcaseDefinition getXmlTestModel(Project project, TestDetail detail) {
        String xmlSource = getSourceCode(project, detail);

        if (!StringUtils.hasText(xmlSource)) {
            throw new ApplicationRuntimeException("Failed to get XML source code for test: " + detail.getPackageName() + "." + detail.getName());
        }

        return ((SpringBeans) new XmlTestMarshaller().unmarshal(new StringSource(xmlSource))).getTestcase();
    }

    /**
     * Get test case model from Java source code.
     * @param project
     * @param detail
     * @return
     */
    private TestcaseDefinition getJavaTestModel(Project project, TestDetail detail) {
        TestcaseDefinition testModel = new TestcaseDefinition();
        testModel.setName(detail.getClassName() + "." + detail.getMethodName());

        //TODO load Java test logic into model

        return testModel;
    }

    /**
     * Gets the current test directory based on project home and default test directory.
     * @return
     */
    private String getTestDirectory(Project project) {
        return new File(project.getProjectHome()).getAbsolutePath() + File.separator +
                project.getSettings().getXmlSrcDirectory();
    }

    /**
     * Gets the current test directory based on project home and default test directory.
     * @return
     */
    private String getJavaDirectory(Project project) {
        return new File(project.getProjectHome()).getAbsolutePath() + File.separator +
                project.getSettings().getJavaSrcDirectory();
    }
}

/*
 * *****************************************************************************
 * The MIT License (MIT)
 * 
 * Copyright (c) 2022, Perforce Software, Inc.  
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of 
 * this software and associated documentation files (the "Software"), to deal in 
 * the Software without restriction, including without limitation the rights to use, 
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the 
 * Software, and to permit persons to whom the Software is furnished to do so, 
 * subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all 
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, 
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE 
 * SOFTWARE.
 * *****************************************************************************
 */

package com.perforce.halm.jenkins;

import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class HALMTestReporterTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    final String name = "Bobby";

    /* Commented out example tests.
    @Test
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.getPublishersList().add(new HALMTestReporter("connectionID",
            "projectID", "automationSuiteName", 123, "*.xml"));
        project = jenkins.configRoundtrip(project);
        jenkins.assertEqualDataBoundBeans(new HALMTestReporter("connectionID",
            "projectID", "automationSuiteName", 123, "*.xml"),
            project.getPublishersList().get(0));
    }

    @Test
    public void testConfigRoundtripFrench() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        HelloWorldBuilder builder = new HelloWorldBuilder(name);
        builder.setUseFrench(true);
        project.getBuildersList().add(builder);
        project = jenkins.configRoundtrip(project);

        HelloWorldBuilder lhs = new HelloWorldBuilder(name);
        lhs.setUseFrench(true);
        jenkins.assertEqualDataBoundBeans(lhs, project.getBuildersList().get(0));
    }

    @Test
    public void testBuild() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        HelloWorldBuilder builder = new HelloWorldBuilder(name);
        project.getBuildersList().add(builder);

        FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);
        jenkins.assertLogContains("Hello, " + name, build);
    }

    @Test
    public void testBuildFrench() throws Exception {

        FreeStyleProject project = jenkins.createFreeStyleProject();
        HelloWorldBuilder builder = new HelloWorldBuilder(name);
        builder.setUseFrench(true);
        project.getBuildersList().add(builder);

        FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);
        jenkins.assertLogContains("Bonjour, " + name, build);
    }

    @Test
    public void testScriptedPipeline() throws Exception {
        String agentLabel = "my-agent";
        jenkins.createOnlineSlave(Label.get(agentLabel));
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "test-scripted-pipeline");
        String pipelineScript
                = "node {\n"
                + "  greet '" + name + "'\n"
                + "}";
        job.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun completedBuild = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
        String expectedString = "Hello, " + name + "!";
        jenkins.assertLogContains(expectedString, completedBuild);
    }
    */

}
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

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import jenkins.tasks.SimpleBuildStep;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import java.io.IOException;
import java.io.Serializable;

public class HALMTestReporter extends Notifier implements SimpleBuildStep, IHALMTestReporterTask, Serializable {
    private static final long serialVersionUID = 1L;
    private static final HALMTestReporterCommon commonFuncs = new HALMTestReporterCommon();

    private String halmConnectionID;
    private String projectID;
    private String automationSuite;
    private long automationSuiteID;
    private String testFilePattern;
    private String testFileFormat;
    private String testRunSet;
    private long testRunSetID;
    private String description;
    private String branch;


    @DataBoundConstructor
    public HALMTestReporter(String halmConnectionID, String projectID, long automationSuiteID,
                            String testFilePattern, long testRunSetID) {
        this.halmConnectionID = halmConnectionID;
        this.projectID = projectID;
        this.automationSuiteID = automationSuiteID;
        this.automationSuite = commonFuncs.getLabelOfAutomationSuite(automationSuiteID);
        this.testFilePattern = testFilePattern;
        this.testRunSetID = testRunSetID;
        this.testRunSet = commonFuncs.getLabelOfTestRunSet(testRunSetID);
        commonFuncs.setLastProjectID(this.projectID);
    }
    @Override
    public BuildStepDescriptor getDescriptor() {
        return (HALMTestReporterDescriptor) super.getDescriptor();
    }

    @Override
    public void perform(@NotNull Run<?, ?> run, @NotNull FilePath workspace, @NotNull EnvVars env,
                        @NotNull Launcher launcher, @NotNull TaskListener listener)
            throws InterruptedException, IOException {

        HALMTestReporterObject reporterObject = new HALMTestReporterObject();
        reporterObject.setHalmConnectionID(getHalmConnectionID());
        reporterObject.setProjectID(getProjectID());
        reporterObject.setAutomationSuiteID(getAutomationSuiteID());
        reporterObject.setTestFilePattern(getTestFilePattern());
        reporterObject.setTestFileFormat(getTestFileFormat());
        reporterObject.setTestRunSetID(getTestRunSetID());
        reporterObject.setDescription(getDescription());
        reporterObject.setBranch(getBranch());

        try {
            commonFuncs.submitBuildToHelixALM(run, workspace, env, launcher, listener, reporterObject);
        }
        catch (Exception e) {
            // Log any errors.
            listener.getLogger().println("An error occurred when submitting the build: " + e.getMessage());

            // Ensure the user can visibly tell something went wrong, assuming
            // that their build hasn't straight up failed prior to this step.
            run.setResult(Result.UNSTABLE);
        }
    }

    @Override
    public void perform(@NotNull Run<?, ?> run, @NotNull EnvVars env, @NotNull TaskListener listener)
            throws InterruptedException, IOException {

        HALMTestReporterObject reporterObject = new HALMTestReporterObject();
        reporterObject.setHalmConnectionID(getHalmConnectionID());
        reporterObject.setProjectID(getProjectID());
        reporterObject.setAutomationSuiteID(getAutomationSuiteID());
        reporterObject.setTestFilePattern(getTestFilePattern());
        reporterObject.setTestFileFormat(getTestFileFormat());
        reporterObject.setTestRunSetID(getTestRunSetID());
        reporterObject.setDescription(getDescription());
        reporterObject.setBranch(getBranch());
        try {
            commonFuncs.submitBuildToHelixALM(run, null, env, null, listener, reporterObject);
        }
        catch (Exception e) {
            // The common handler will have already logged the errors.
            // Ensure the user can visibly tell something went wrong, assuming
            // that their build hasn't straight up failed prior to this step.
            listener.getLogger().println("An error occurred when submitting the build: " + e.getMessage());
            run.setResult(Result.UNSTABLE);
        }
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return super.getRequiredMonitorService();
    }

    @Override
    public String getHalmConnectionID() {
        return halmConnectionID;
    }

    @DataBoundSetter
    public final void setHalmConnectionID(String halmConnectionID) {
        this.halmConnectionID = halmConnectionID;
    }

    @Override
    public String getProjectID() {
        return projectID;
    }

    @DataBoundSetter
    public final void setProjectID(String projectID) {
        this.projectID = projectID;
    }

    @DataBoundSetter
    public final void setAutomationSuite(String automationSuite) {
        try {
            this.automationSuite = commonFuncs.getLabelOfAutomationSuite(Long.parseLong(automationSuite));
            this.automationSuiteID = Long.parseLong(automationSuite);
        }
        catch (Exception ex) {
            commonFuncs.logFromCaller(ex.getMessage());
            this.automationSuite = "";
            this.automationSuiteID = -1;
        }
    }

    @Override
    public String getAutomationSuite() {
        return this.automationSuite;
    }

    public long getAutomationSuiteID() {
        return this.automationSuiteID;
    }

    @DataBoundSetter
    public final void setAutomationSuiteID(long automationSuiteID) {
        try {
            this.automationSuiteID = automationSuiteID;
            this.automationSuite = commonFuncs.getLabelOfAutomationSuite(automationSuiteID);
        }
        catch (Exception ex) {
            commonFuncs.logFromCaller(ex.getMessage());
            this.automationSuiteID = -1; // Invalid ID number.
            this.automationSuite = "";
        }
    }

    @Override
    public String getTestFilePattern() {
        return testFilePattern;
    }

    @DataBoundSetter
    public final void setTestFilePattern(String testFilePattern) {
        this.testFilePattern = testFilePattern;
    }

    @Override
    public String getTestRunSet() {
        return testRunSet;
    }

    @DataBoundSetter
    public final void setTestRunSet(String testRunSet) {
        try {
            this.testRunSet = commonFuncs.getLabelOfTestRunSet(Long.parseLong(testRunSet));
            this.testRunSetID = Long.parseLong(testRunSet);
        }
        catch (Exception ex) {
            commonFuncs.logFromCaller(ex.getMessage());
            this.testRunSet = ""; // Invalid ID number.
            this.testRunSetID = -1;
        }
    }

    @Override
    public long getTestRunSetID() {
        return testRunSetID;
    }

    @DataBoundSetter
    public final void setTestRunSetID(long testRunSetID) {
        this.testRunSetID = testRunSetID;
        this.testRunSet = commonFuncs.getLabelOfTestRunSet(testRunSetID);
    }

    @Override
    public String getTestFileFormat() {
        return testFileFormat;
    }

    @DataBoundSetter
    public final void setTestFileFormat(String testFileFormat) {
        this.testFileFormat = testFileFormat;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @DataBoundSetter
    public final void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getBranch() {
        return branch;
    }

    @DataBoundSetter
    public final void setBranch(String branch) {
        this.branch = branch;
    }

    @Override
    public boolean getShowOptionalSettings() {
        return !(getTestRunSet() == null || getTestRunSet().isEmpty()) ||
            !(getDescription() == null || getDescription().isEmpty()) ||
            !(getBranch() == null ||getBranch().isEmpty());
    }

}

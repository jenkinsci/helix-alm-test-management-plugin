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

package com.perforce.halm.jenkins.pipeline;


import com.perforce.halm.jenkins.HALMTestReporterCommon;
import com.perforce.halm.jenkins.IHALMTestReporterTask;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class HALMTestReporterStep extends Step implements IHALMTestReporterTask {
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

    // It is only possible to have one DataBoundConstructor;
    // any additional ones will throw a compile error.
    @DataBoundConstructor
    public HALMTestReporterStep(String halmConnectionID, String projectID, long automationSuiteID,
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
    public StepExecution start(StepContext context) throws Exception {
        return new HALMTestReporterStepExecution(this, context);
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
    public final void setAutomationSuite(String automationSuiteName) {
        try {
            this.automationSuite = commonFuncs.getLabelOfAutomationSuite(Long.parseLong(automationSuiteName));
            this.automationSuiteID = Long.parseLong(automationSuiteName);
        }
        catch (Exception ex) {
            commonFuncs.logFromCaller(ex.getMessage());
            this.automationSuite = automationSuiteName; // We save the name so we can check the server if this exists later
            // when a step executes.
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
            this.automationSuite = "";
            this.automationSuiteID = -1; // Invalid ID number.
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
    public String getTestFileFormat() { return testFileFormat; }

    @DataBoundSetter
    public final void setTestFileFormat(String testFileFormat) {
        this.testFileFormat = testFileFormat;
    }
    @Override
    public String getTestRunSet() {
        // By checking for null, we can avoid displaying blank optional fields
        // the Jenkins Junit plugin code has a similar pattern.
        return this.testRunSet == null ? "" : this.testRunSet;
    }

    @DataBoundSetter
    public final void setTestRunSet(String testRunSet) {
        try {
            this.testRunSet = commonFuncs.getLabelOfTestRunSet(Long.parseLong(testRunSet));
            this.testRunSetID = Long.parseLong(testRunSet);
        }
        catch (Exception ex) {
            commonFuncs.logFromCaller(ex.getMessage());
            this.testRunSet = testRunSet; // We save the name so we can check the server if this exists later
            // when a step executes.
            this.testRunSetID = -1;
        }
    }

    @Override
    public long getTestRunSetID() {
        return this.testRunSetID;
    }

    @DataBoundSetter
    public final void setTestRunSetID(long testRunSetID) {
        this.testRunSetID = testRunSetID;
        this.testRunSet = commonFuncs.getLabelOfTestRunSet(testRunSetID);
    }

    @Override
    public String getDescription() {
        return this.description == null ? "" : this.description;
    }

    @DataBoundSetter
    public final void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getBranch() {
        return this.branch == null ? "" : this.branch;
    }

    @DataBoundSetter
    public final void setBranch(String branch) {
        this.branch = branch;
    }


    @Override
    public boolean getShowOptionalSettings() {
        return !getTestRunSet().isEmpty() ||
            !getDescription().isEmpty() ||
            !getBranch().isEmpty();
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        private static final HALMTestReporterCommon commonFuncs =
                new HALMTestReporterCommon();
        @Override
        public String getFunctionName() {
            return "halm_report";
        }


        @Override
        @NonNull
        public String getDisplayName() {
            return "Helix ALM Test Results Reporter";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            Set<Class<?>> context = new HashSet<>();
            Collections.addAll(context, FilePath.class, FlowNode.class, TaskListener.class, Launcher.class);
            return Collections.unmodifiableSet(context);
        }

        /**
         * Populates the 'credentialType' dropdown with appropriate values.
         *
         * @return Credential Type dropdown list values.
         */
        public ListBoxModel doFillHalmConnectionIDItems() {
            Jenkins.get().checkPermission(Job.CONFIGURE);
            return commonFuncs.doFillHalmConnectionIDItems();
        }

        /**
         * Checks the entered connection ID.
         *
         * @param halmConnectionID - the currently selected connection ID.
         * @return error if the connection is invalid (can't connect).
         */
        @POST
        public FormValidation doCheckHalmConnectionID(@QueryParameter("value") final String halmConnectionID) {
            return commonFuncs.doHalmConnectionIDValidation(halmConnectionID);
        }

        /**
         * Populates the 'projectID' dropdown based on the selection of the connectionID dropdown.
         *
         * @param halmConnectionID Currently selected halmConnectionID
         * @return Populated list of entries for the projectID dropdown.
         */
        public ListBoxModel doFillProjectIDItems(@QueryParameter("halmConnectionID") final String halmConnectionID) {
            Jenkins.get().checkPermission(Job.CONFIGURE);
            return commonFuncs.doFillProjectIDItems(halmConnectionID);
        }

        /**
         * Validates the input to the projectID field.
         *
         * @param projectID - The current value of the projectID field.
         * @return OK unless the item is blank (projectID cannot be blank).
         */
        @POST
        public FormValidation doCheckProjectID(@QueryParameter("value") final String projectID) {
            return commonFuncs.doProjectIDValidation(projectID);
        }

        /**
         * Populates the Automation Suite dropdown based on the selected connection and project.
         *
         * @param halmConnectionID Currently selected HALM Connection UUID
         * @param projectID Currently selected HALM Project UUID
         * @return Populated list of entries for the automationSuiteName dropdown
         */
        public ListBoxModel doFillAutomationSuiteIDItems(@QueryParameter("halmConnectionID") final String halmConnectionID,
                                                         @QueryParameter("projectID") final String projectID) {
            Jenkins.get().checkPermission(Job.CONFIGURE);
            return commonFuncs.doFillAutomationSuiteIDItems(halmConnectionID, projectID);
        }

        /**
         * Validates the input to the automationSuite field.
         *
         * @param automationSuiteID - The current value of the automationSuite field.
         * @return OK unless the item is blank (automationSuite cannot be blank).
         */
        @POST
        public FormValidation doCheckAutomationSuiteID(@QueryParameter("value") final String automationSuiteID) {
            return commonFuncs.doAutomationSuiteIDValidation(automationSuiteID);
        }

        /**
         * Populates the Test File Format dropdown.
         *
         * @return Populated list of entries for the testFileFormat dropdown.
         */
        public ListBoxModel doFillTestFileFormatItems() {
            Jenkins.get().checkPermission(Job.CONFIGURE);
            return commonFuncs.doFillTestFileFormatItems();
        }

        /**
         * Populates the Test Run Set dropdown.
         *
         * @param halmConnectionID Currently selected HALM Connection UUID
         * @param projectID Currently selected HALM Project UUID
         * @return Populated list of entries for the testRunSet dropdown
         */
        public ListBoxModel doFillTestRunSetIDItems(@QueryParameter("halmConnectionID") final String halmConnectionID,
                                                    @QueryParameter("projectID") final String projectID) {
            Jenkins.get().checkPermission(Job.CONFIGURE);
            return commonFuncs.doFillTestRunSetIDItems(halmConnectionID, projectID);
        }
    }
}

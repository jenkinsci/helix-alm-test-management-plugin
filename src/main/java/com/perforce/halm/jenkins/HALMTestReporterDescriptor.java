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

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;


/**
 * Runs validation & dynamic content on the HALMTestReporter/config.jelly
 */
@Extension
public class HALMTestReporterDescriptor extends BuildStepDescriptor<Publisher> {
    private static HALMTestReporterCommon commonFuncs;

    /**
     * Constructor
     */
    public HALMTestReporterDescriptor() {
        super(HALMTestReporter.class);
        commonFuncs = new HALMTestReporterCommon("jenkins.HALMTestReporterDescriptor");
        load();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
        return jobType == FreeStyleProject.class;
    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Override
    public String getDisplayName() {
        return "Helix ALM Test Results Reporting";
    }

    /**
     * Populates the 'credentialType' dropdown with appropriate values.
     *
     * @return Credential Type dropdown list values.
     */
    public ListBoxModel doFillHalmConnectionIDItems() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
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
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
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
     * Populates the Automation Suite dropdown based on the selected connection & project.
     *
     * @param halmConnectionID Currently selected HALM Connection UUID
     * @param projectID Currently selected HALM Project UUID
     * @return Populated list of entries for the automationSuiteName dropdown
     */
    public ListBoxModel doFillAutomationSuiteIDItems(@QueryParameter("halmConnectionID") final String halmConnectionID,
                                                     @QueryParameter("projectID") final String projectID) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
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
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        return commonFuncs.doFillTestFileFormatItems();
    }

    /**
     * Populates the Test Run Set dropdown.
     *
     * @param halmConnectionID Currently selected HALM Connection UUID
     * @param projectID Currently selected HALM Project UUID
     * @return Populated list of entries for the automationSuiteName dropdown
     */
    public ListBoxModel doFillTestRunSetIDItems(@QueryParameter("halmConnectionID") final String halmConnectionID,
                                                @QueryParameter("projectID") final String projectID) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        return commonFuncs.doFillTestRunSetIDItems(halmConnectionID, projectID);
    }
}

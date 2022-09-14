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
import com.perforce.halm.jenkins.HALMTestReporterObject;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.*;
import hudson.model.*;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import static java.util.Objects.requireNonNull;

public class HALMTestReporterStepExecution extends SynchronousStepExecution<Object> {
    private static final HALMTestReporterCommon commonFuncs =
            new HALMTestReporterCommon("HALMTestReporterStepExecution");
    private transient final HALMTestReporterStep step;
    protected HALMTestReporterStepExecution(@NonNull HALMTestReporterStep step, StepContext context) {
        super(context);
        this.step = step;
    }

    /**
     * Overrides the function from the superclass.
     * @return whether the execution was successful or not
     */
    @Override
    public Object run() throws Exception {
        // Declare the run and listener outside the below try/catch
        // so that they can be used by the catch block if needed.
        TaskListener listener = null;
        Run<?,?> run = null;
        try {
            FilePath workspace = getContext().get(FilePath.class);
            if (workspace != null) {
                workspace.mkdirs();
            }
            run = requireNonNull(getContext().get(Run.class));
            listener = getContext().get(TaskListener.class);
            Launcher launcher = getContext().get(Launcher.class);
            // Branch may be supplied as a variable, so we'll need EnvVars to not be null
            // to check that.
            EnvVars env = requireNonNull(getContext().get(EnvVars.class));

            HALMTestReporterObject reporterObject = new HALMTestReporterObject();
            String halmConnID = step.getHalmConnectionID();
            reporterObject.setHalmConnectionID(halmConnID);
            String projID = step.getProjectID();
            reporterObject.setProjectID(projID);
            reporterObject.setTestFilePattern(step.getTestFilePattern());
            reporterObject.setTestFileFormat(step.getTestFileFormat());
            reporterObject.setDescription(env.expand(step.getDescription()));
            reporterObject.setBranch(env.expand(step.getBranch()));

            // Check if we were supplied names or ids for suites and run sets
            if (step.getAutomationSuiteID() <= 0) {
                // our sentinel value for "unset" is -1.
                String checkName = env.expand(step.getAutomationSuite());
                try {
                    reporterObject.setAutomationSuiteID(Long.parseLong(commonFuncs.getIDOfAutomationSuite(checkName, halmConnID, projID)));
                }
                catch (Exception e) {
                    throw new Exception(String.format("Could not find an automation suite with name %s.", checkName));
                }
            }
            else {
                reporterObject.setAutomationSuiteID(step.getAutomationSuiteID());
            }
            if (step.getTestRunSetID() <= 0) {
                if (step.getTestRunSet() == null || step.getTestRunSet().isEmpty()) {
                    reporterObject.setTestRunSetID(-1); // this will tell the submitter to ignore the test run set
                }
                else {
                    String checkSet = env.expand(step.getTestRunSet());
                    try {
                        reporterObject.setTestRunSetID(Long.parseLong(commonFuncs.getIDOfTestRunSet(checkSet, halmConnID, projID)));
                    }
                    catch (Exception e) {
                        throw new Exception(String.format("Could not find a test run set with label %s.", checkSet));
                    }
                }
            }
            commonFuncs.submitBuildToHelixALM(run, workspace, env, launcher, listener, reporterObject);
        }
        catch (Exception e) {
            // The common handler will have already logged most errors.
            // if errors happen during the set up for this, then we
            // should make sure we log it to the steps.
            assert listener != null;
            listener.getLogger().println("An error occurred when submitting the build: " + e.getMessage());
            run.setResult(Result.UNSTABLE);
        }
        return new Object();
    }
}

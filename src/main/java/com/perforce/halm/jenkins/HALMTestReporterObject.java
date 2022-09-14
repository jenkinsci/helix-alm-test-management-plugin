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

/**
 * Class object containing necessary fields for build submission
 * Used to submit information to the common function in one object.
 */
public class HALMTestReporterObject {
    private String halmConnectionID;
    private String projectID;
    private long automationSuiteID;
    private String testFilePattern;
    private String testFileFormat;
    private String testRunSet;
    private long testRunSetID;
    private String description;
    private String branch;

    public HALMTestReporterObject() {
        this.halmConnectionID = null;
        this.projectID = null;
        this.automationSuiteID = -1;
        this.testFilePattern = null;
        this.testFileFormat = null;
        this.testRunSet = null;
        this.testRunSetID = -1;
        this.description = null;
        this.branch = null;
    }

    public HALMTestReporterObject(String connID, String projID, long suiteID,
                                  String filePattern, String fileFormat, long setID,
                                  String desc, String b) {
        this.halmConnectionID = connID;
        this.projectID = projID;
        this.automationSuiteID = suiteID;
        this.testFilePattern = filePattern;
        this.testFileFormat = fileFormat;
        this.testRunSet = null;
        this.testRunSetID = setID;
        this.description = desc;
        this.branch = b;
    }

    /**
     * Gets the selected Helix ALM connection ID, or throws an exception
     * if no selection is made.
     *
     * @return - the connectionID assigned to this object.
     * @throws NullPointerException - if halmConnectionID is null.
     */
    public String getHalmConnectionID() throws NullPointerException {
        if (this.halmConnectionID == null || this.halmConnectionID.isEmpty()) {
            throw new NullPointerException("You must select a Helix ALM connection.");
        }
        return this.halmConnectionID;
    }

    public void setHalmConnectionID(String connID) {
        this.halmConnectionID = connID;
    }

    /**
     * Gets the selected Helix ALM project ID, or throws an exception
     * if no selection is made.
     *
     * @return - the projectID assigned to this object.
     * @throws NullPointerException - if projectID is null.
     */
    public String getProjectID() throws Exception {
        if (this.projectID == null || this.projectID.isEmpty()) {
            throw new Exception("You must select a Helix ALM project.");
        }
        return this.projectID;
    }

    public void setProjectID(String projID) {
        this.projectID = projID;
    }

    /**
     *
     * @return the selected automation suite.
     * @throws Exception - if automationSuiteID is invalid
     */
    public long getAutomationSuiteID() throws Exception {
        if (this.automationSuiteID <= 0) {
            throw new Exception("You must select an Automation Suite.");
        }
        return this.automationSuiteID;
    }

    public void setAutomationSuiteID(long suiteID) {
        this.automationSuiteID = suiteID;
    }

    public String getTestFilePattern() throws Exception {
        if (this.testFilePattern == null || this.testFilePattern.isEmpty()) {
            throw new Exception("You must enter a Test Report Files pattern.");
        }
        return this.testFilePattern;
    }

    public void setTestFilePattern(String filePattern) {
        this.testFilePattern = filePattern;
    }

    public String getTestFileFormat() {
        return this.testFileFormat;
    }

    public void setTestFileFormat(String fileFormat) {
        this.testFileFormat = fileFormat;
    }

    /**
     * Gets the ID of a test run set so the build can be associated with it.
     *
     * @return the id of the test run set. -1 if unset.
     */
    public long getTestRunSetID() {
        return this.testRunSetID;
    }

    /**
     * Sets the ID of a test run set.
     *
     * @param setID - The ID of the test run set to send up to the server
     */
    public void setTestRunSetID(long setID) {
        this.testRunSetID = setID;
    }

    /**
     * Gets the name of a test run set so the build can be associated with it.
     *
     * @return the name of the test run set. Null if unset.
     */
    public String getTestRunSet() {
        return this.testRunSet;
    }

    /**
     * Sets the name of the test run set to associate to the build.
     *
     * @param set - the test run set's name
     */
    public void setTestRunSet(String set) {
        this.testRunSet = set;
    }

    public String getBranch() {
        return this.branch;
    }

    public void setBranch(String b) {
        this.branch = b;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String d) {
        this.description = d;
    }
}

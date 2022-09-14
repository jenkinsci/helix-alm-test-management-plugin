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

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.perforce.halm.jenkins.globalconfig.HALMConnection;
import com.perforce.halm.jenkins.globalconfig.HALMGlobalConfig;
import com.perforce.halm.reportingtool.BuildSubmitter;
import com.perforce.halm.reportingtool.format.ReportFormatType;
import com.perforce.halm.reportingtool.models.BuildMetadata;
import com.perforce.halm.reportingtool.models.HelixALMSuiteContext;
import com.perforce.halm.reportingtool.models.ReportContext;
import com.perforce.halm.rest.*;
import com.perforce.halm.rest.responses.SubmitAutomationBuildResponse;
import com.perforce.halm.rest.types.IDLabelPair;
import com.perforce.halm.rest.types.NameValuePair;
import com.perforce.halm.rest.types.Project;
import com.perforce.halm.rest.types.administration.MenuItem;
import com.perforce.halm.rest.types.automation.suite.AutomationSuite;
import com.perforce.halm.rest.types.automation.jenkins.AutomationBuildRunConfigurationJenkins;
import com.perforce.halm.rest.types.automation.jenkins.JenkinsBuildParameter;
import com.perforce.halm.rest.types.automation.jenkins.JenkinsBuildParameterText;
import feign.FeignException;
import hudson.*;
import hudson.model.*;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import okhttp3.HttpUrl;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Class for common field operations for HALMTestReporterDescriptor and HALMTestReporterStep.
 */
public class HALMTestReporterCommon {

    private static Logger logger;
    private enum clearIndexes {
            SUITES,
            RUNSETS
    }
    private String lastProjectID = "";
    private static final String MENU_ID = "2147483637"; // the menuID for the Run Set menu.
    private static final HashMap<String, String> cachedSuites = new HashMap<>();
    private static final HashMap<String, String> cachedRunSets = new HashMap<>();
    private static final boolean[] cleared = { true, true };

    // A list of environment variables to extract from tne environment and display under
    // properties.
    private static final String[] envVars = { "BUILD_TAG", "JOB_NAME", "NODE_NAME", "WORKSPACE", "JAVA_HOME" };

    /**
     * Initializes a Logger for the class calling this.
     * @param className - the name of the caller
     */
    public HALMTestReporterCommon(String className) {
        logger = Logger.getLogger("jenkins." + className);
    }

    public void setLastProjectID(String projectID) {
        this.lastProjectID = projectID;
        // Signal to the cache that it's time to flush the run sets and suites caches
        cleared[clearIndexes.SUITES.ordinal()] = true;
        cleared[clearIndexes.RUNSETS.ordinal()] = true;

        cachedSuites.clear();
        cachedRunSets.clear();
    }

    /**
     * Checks the validity of the current connection.
     *
     * @return Whether the connection is valid (can we connect at all?)
     */
    public FormValidation doHalmConnectionIDValidation(final String value) {
        if (value == null || value.isEmpty()) {
            return FormValidation.error("You must select a Helix ALM connection.");
        }
        try {
            HALMConnection connection = HALMGlobalConfig.get().getConnectionByNameOrID(value);
            if (connection != null) {
                Client c = new Client(getConnectionInfo(connection));
                CertificateStatus certs = c.getServerCertStatus().getStatus();
                if (certs == CertificateStatus.INVALID || certs == CertificateStatus.INVALID_DOWNLOADABLE) {
                    // Here, we throw an error back if the certs are invalid or untrusted
                    return FormValidation.error("The SSL certificates for the selected Helix ALM connection are either " +
                            "invalid or untrusted. Check the Helix ALM connection settings for more information.");
                }
                if (c.getProjects().getProjects().size() <= 0) {
                    return FormValidation.error("The specified user does not have permission to access any " +
                            "Helix ALM projects using the REST API.");
                }
            }
            else {
                logger.log(Level.INFO, "The selected Helix ALM connection no longer exists. Select a " +
                        "different connection.");
                return FormValidation.error("The selected Helix ALM connection no longer exists. Select a " +
                        "different connection.");
            }
        }
        catch (Exception ex) {
            logger.log(Level.INFO, ex.getMessage());
            if (ex instanceof FeignException.Unauthorized ||
                    ex instanceof FeignException.Forbidden ||
                    ex instanceof FeignException.InternalServerError) {
                return FormValidation.error("The specified credentials are invalid. Check the credentials and try again.");
            }
            else {

                return FormValidation.error("An unexpected error occurred. Try again later.");
            }
        }
        return FormValidation.ok();
    }

    /**
     * Checks if a Helix ALM project has been selected. Should not be able to
     * select a project that does not exist.
     *
     * @return FormValidation.error if the project is somehow empty, OK otherwise
     */
    public FormValidation doProjectIDValidation(final String value) {
        if (value == null || value.isEmpty()) {
            return FormValidation.error("You must select a Helix ALM project.");
        }
       return FormValidation.ok();
    }

    /**
     * Checks if an automation suite has been selected. Should not be possible to
     * get a suite that does not exist.
     *
     * @return FormValidation.error if the suite is somehow empty, OK otherwise
     */
    public FormValidation doAutomationSuiteIDValidation(final String value) {
        if (value == null || value.isEmpty()) {
            return FormValidation.error("You must select a Helix ALM automation suite.");
        }
        return FormValidation.ok();
    }

    /**
     * Populates the 'credentialType' dropdown with appropriate values.
     *
     * @return Credential Type dropdown list values.
     */
    public ListBoxModel doFillHalmConnectionIDItems() {
        ListBoxModel items = new ListBoxModel();
        items.add("", ""); // For no selection; the end user must select an item once items is filled up.
        for(HALMConnection connection : HALMGlobalConfig.get().getConnections()) {
            items.add(connection.getConnectionName(), connection.getConnectionUUID());
        }

        return items;
    }

    /**
     * Populates the 'projectID' dropdown based on the selection of the connectionID dropdown.
     *
     * @param halmConnectionID Currently selected halmConnectionID
     * @return Populated list of entries for the projectID dropdown.
     */
    public ListBoxModel doFillProjectIDItems(final String halmConnectionID) {

        ListBoxModel items = new ListBoxModel();
        items.add("", ""); // For no selection; the end user must select an item once items is filled up.
        if (halmConnectionID == null || halmConnectionID.isEmpty()) {
            return items;
        }
        try {
            HALMConnection connection = HALMGlobalConfig.get().getConnectionByNameOrID(halmConnectionID);
            if (connection != null) {
                Client c = new Client(getConnectionInfo(connection));
                List<Project> projList = c.getProjects().getProjects();
                for (Project p : projList) {
                    items.add(p.getName(), p.getUuid());
                }
            }
            else {
                logger.log(Level.INFO, "The selected Helix ALM connection no longer exists. Select a different connection.");
            }
        }
        catch (Exception ex) {
            logger.log(Level.ALL, "An error occurred when populating the Project list. " + ex.getMessage());
        }

        return items;
    }

    /**
     * Populates the Automation Suite dropdown based on the selected connection & project.
     *
     * @param halmConnectionID Currently selected HALM Connection UUID
     * @param projectID Currently selected HALM Project UUID
     * @return Populated list of entries for the automationSuiteName dropdown
     */
    public ListBoxModel doFillAutomationSuiteIDItems(final String halmConnectionID,
                                                     final String projectID) {

        ListBoxModel items = new ListBoxModel();
        items.add("", ""); // For no selection; the end user must select an item once items is filled up.
        if (halmConnectionID == null || halmConnectionID.isEmpty()
                || projectID == null || projectID.isEmpty()) {
            return items;
        }

        if (!lastProjectID.equals(projectID)) {
            setLastProjectID(projectID);
        }
        try {
            HALMConnection connection = HALMGlobalConfig.get().getConnectionByNameOrID(halmConnectionID);
            if (connection != null) {
                Client c = new Client(getConnectionInfo(connection));
                List<AutomationSuite> autoSuites = c.getAutomationSuites(projectID);
                for (AutomationSuite s : autoSuites) {
                    items.add(s.getName(), Integer.toString(s.getID()));
                    if (cleared[clearIndexes.SUITES.ordinal()]) {
                        cachedSuites.put(Integer.toString(s.getID()), s.getName());
                    }
                }
                if (autoSuites.isEmpty()) {
                    logger.log(Level.WARNING, "The Helix ALM project does not contain any automation suites.");
                }
                // Signal that we do not need to populate the cache
                cleared[clearIndexes.SUITES.ordinal()] = false;
            }
            else {
                logger.log(Level.INFO, "The selected Helix ALM connection no longer exists. Select a different connection.");
            }
        }
        catch (Exception ex) {
            logger.log(Level.ALL, "An error occurred when populating the Automation suite list. " + ex.getMessage());
        }

        return items;
    }

    /**
     * Populates the Test File Format dropdown.
     *
     * @return Populated list of entries for the testFileFormat dropdown.
     */
    public ListBoxModel doFillTestFileFormatItems() {
        ListBoxModel items = new ListBoxModel();

        try {
            for (ReportFormatType t : ReportFormatType.values()) {
                items.add(t.name(), Integer.toString(t.ordinal()));
            }
        }
        catch (Exception ex) {
            logger.log(Level.ALL, "An error occurred when populating the Report file format list. " +
                    ex.getMessage());
        }
        return items;
    }

    /**
     * Populates the Test Run Set dropdown.
     *
     * @param halmConnectionID Currently selected HALM Connection UUID
     * @param projectID Currently selected HALM Project UUID
     * @return Populated list of entries for the testRunSet dropdown
     */
    public ListBoxModel doFillTestRunSetIDItems(final String halmConnectionID,
                                                final String projectID) {

        ListBoxModel items = new ListBoxModel();

        // We are adding an 'empty' value to indicate unselected.
        items.add("", "");

        if (halmConnectionID == null || halmConnectionID.isEmpty()
                || projectID == null || projectID.isEmpty()) {
            return items;
        }

        if (!lastProjectID.equals(projectID)) {
            setLastProjectID(projectID);
        }
        try {
            HALMConnection connection = HALMGlobalConfig.get().getConnectionByNameOrID(halmConnectionID);
            if (connection != null) {
                Client c = new Client(getConnectionInfo(connection));
                List<MenuItem> menuItems = c.getMenu(projectID, MENU_ID).getItems();
                for (MenuItem item : menuItems) {
                    items.add(item.getLabel(), item.getId().toString());
                    if (cleared[clearIndexes.RUNSETS.ordinal()]) {
                        cachedRunSets.put(item.getId().toString(), item.getLabel());
                    }
                }
                if (menuItems.isEmpty()) {
                    logger.log(Level.INFO, "The Helix ALM project does not contain any test run sets.");
                }
                // Signal we no longer need to populate the cache.
                cleared[clearIndexes.RUNSETS.ordinal()] = false;
            }
            else {
                logger.log(Level.INFO, "The selected Helix ALM connection no longer exists. Select a different connection.");
            }
        }
        catch (Exception ex) {
            logger.log(Level.ALL, "An error occurred when populating the Test run set list. " + ex.getMessage());
        }

        return items;
    }

    /**
     * From the ordinal expressed as a string, get the ReportFormatType enum with that ordinal.
     *
     * @param ord - the ordinal as it is in the list box.
     * @return - the enum, or null if we somehow got a bad ordinal
     */
    public static ReportFormatType getTypeFromOrdinal(String ord) {
        try {
            return ReportFormatType.values()[Integer.parseInt(ord)];
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "An error occurred when parsing the report file format type: " + e.getMessage());
            return null;
        }
    }

    /**
     * Determines the OS with the supplied Platform enum.
     *
     * @param p - the Platform from the environment, will be WINDOWS or UNIX
     * @return A string representing the operating system the tests were run on.
     */
    public static String determineOS(Platform p) {
        if (p == Platform.UNIX) {
            if (Platform.isDarwin() || Platform.isSnowLeopardOrLater()) {
                return "Mac OS X";
            }
            else {
                return "Linux";
            }
        }
        else {
            return "Windows";
        }
    }

    /**
     * Gets the connection info from a selected connection. This is a public function so that
     * its callers can use it as needed.
     *
     * @param connection - the selected HALMConnection
     * @return the connectionInfo for the HALMConnection.
     * @throws Exception if anything fails.
     */
    public static ConnectionInfo getConnectionInfo(@NotNull HALMConnection connection) throws Exception {
        ConnectionInfo connectionInfo;
        String halmUrl = connection.getHalmAPIAddress();
        //Objects.requireNonNull is here to suppress an IntelliJ warning.
        HttpUrl urlObj = Objects.requireNonNull(HttpUrl.parse(halmUrl))
                .newBuilder()
                .build();
        String finalURL = urlObj.toString();
        Iterable<StandardCredentials> credentialSearch = CredentialsProvider.lookupCredentials(StandardCredentials.class,
                Jenkins.get(), ACL.SYSTEM, Collections.emptyList());
        StandardCredentials credentials = CredentialsMatchers.firstOrNull(
                credentialSearch,
                CredentialsMatchers.withId(connection.getCredentialsID())
        );
        switch (connection.getCredentialsType()) {
            case basic:
                // If StringCredentials were submitted as basic auth,
                // assume the format is <user>:<pass>.
                if (credentials instanceof StringCredentials) {
                    StringCredentials apiKeyCreds = (StringCredentials)credentials;

                    // Split the APIKey accordingly
                    String[] tokens = apiKeyCreds.getSecret().getPlainText().split(":");
                    // The first half of the tokens will contain the id of the api key,
                    // the second will contain the "secret" section of the api key.
                    connectionInfo = new ConnectionInfo(finalURL,
                            new AuthInfoAPIKey(tokens[0], tokens[1]));
                }
                else {
                    StandardUsernamePasswordCredentials userpass = (StandardUsernamePasswordCredentials) credentials;
                    connectionInfo = new ConnectionInfo(finalURL,
                            new AuthInfoBasic(userpass.getUsername(), userpass.getPassword().getPlainText()));
                }
                break;
            case apiKey:
                // check if StringCredentials were submitted - StringCredentials are secret text.
                if (credentials instanceof StringCredentials) {
                    StringCredentials apiKeyCreds = (StringCredentials)credentials;

                    // Split the APIKey accordingly
                    String[] tokens = apiKeyCreds.getSecret().getPlainText().split(":");
                    // The first half of the tokens will contain the id of the api key,
                    // the second will contain the "secret" section of the api key.
                    connectionInfo = new ConnectionInfo(finalURL,
                            new AuthInfoAPIKey(tokens[0], tokens[1]));
                }
                else { // Assume StandardUsernamePasswordCredentials.
                    StandardUsernamePasswordCredentials userpass = (StandardUsernamePasswordCredentials)credentials;
                    // Even if the ApiKey's username is entered as secret, you can still getUsername()
                    // from the object without any problems.
                    connectionInfo = new ConnectionInfo(finalURL,
                            new AuthInfoAPIKey(userpass.getUsername(), userpass.getPassword().getPlainText()));
                }
                break;
            default: // This technically shouldn't need to be here, but is here to prevent warnings from IntelliJ.
                throw new Exception("The authentication type is invalid for the Helix ALM connection."); // Throw an exception if we somehow have invalid creds
        }
        if (halmUrl.startsWith("https://")) {
            connectionInfo.setPemCertContents(connection.getAcceptedSSLCertificates());
        }
        return connectionInfo;
    }

    public static StandardCredentials getCredentialsInfo(@NotNull HALMConnection connection) {
        Iterable<StandardCredentials> credentialSearch = CredentialsProvider.lookupCredentials(StandardCredentials.class,
                Jenkins.get(), ACL.SYSTEM, Collections.emptyList());

        return CredentialsMatchers.firstOrNull(
                credentialSearch,
                CredentialsMatchers.withId(connection.getCredentialsID())
        );
    }

    /**
     * Gets the label (or name) of an automation suite given its ID, or null if nothing exists.
     * WARNING: The cache doesn't work for pipelines; you will always get null due to how
     * pipelines work.
     *
     * @param id - The id of the automation suite returned from the REST API.
     * @return The suite's name from cache.
     */
    public String getLabelOfAutomationSuite(long id) {
        return cachedSuites.get(Long.toString(id));
    }

    /**
     * Gets the label of a test run set given its ID, or null if nothing exists.
     * WARNING: The cache doesn't work for pipelines; you will always get null due to how
     * pipelines work.
     *
     * @param id - the id of the test run set returned from the REST API.
     * @return The set's name from cache.
     */
    public static String getLabelOfTestRunSet(long id) {
        return cachedRunSets.get(Long.toString(id));
    }

    /**
     * Gets the id (or key) of the first automation suite with the given name, or null if nothing exists
     *
     * @param name - The name of the automation suite returned from the REST API.
     * @param connID - the connection ID
     * @param projID - the Helix ALM project ID
     * @return the suite's ID from cache.
     */
    public String getIDOfAutomationSuite(String name, String connID, String projID) {
        String retVal = null;
        try {
            HALMConnection connection = HALMGlobalConfig.get().getConnectionByNameOrID(connID);
            if (connection != null) {
                Client c = new Client(getConnectionInfo(connection));
                List<AutomationSuite> autoSuites = c.getAutomationSuites(projID);
                for (AutomationSuite suite : autoSuites) {
                    if (suite.getName().equals(name)) {
                        retVal = suite.getID().toString();
                        break;
                    }
                }
            }
        }
        catch (Exception e) {
            // if errors happen during the check, retVal will still be null and that
            // will throw an error.
        }
        return retVal;
    }

    /**
     * Gets the id (or key) of the first test run set with the given name, or null if nothing exists
     *
     * @param name - The name of the automation suite returned from the REST API.
     * @param connID - the connection ID
     * @param projID - the Helix ALM project ID
     * @return the suite's ID from cache.
     */
    public String getIDOfTestRunSet(String name, String connID, String projID) {
        String retVal = null;
        try {
            HALMConnection connection = HALMGlobalConfig.get().getConnectionByNameOrID(connID);
            if (connection != null) {
                Client c = new Client(getConnectionInfo(connection));
                List<MenuItem> menuItems = c.getMenu(projID, MENU_ID).getItems();
                for (MenuItem item : menuItems) {
                    if (item.getLabel().equals(name)) {
                        retVal = item.getId().toString();
                        break;
                    }
                }
            }
        }
        catch (Exception e) {
            // if errors happen during the check, retVal will still be null and that
            // will throw an error.
        }
        return retVal;
    }

    /**
     * Writes a message to log from its caller.
     *
     * @param message - The message to write to log.
     */
    public void logFromCaller(String message) {
        logger.log(Level.WARNING, message);
    }

    /**
     * Common function to submit test results to Helix ALM.
     *
     * @param run - The Jenkins build that called this function.
     * @param workspace - The workspace generated by the build. If null,
     *                    default to the first "root" of the jenkins server.
     * @param env - The environment that the build runs with.
     * @param launcher - The launcher object generated by a build.
     * @param listener - the logger for the run object. If null, write to this object's log instead.
     * @param reportObj - The report object, supplied by the caller.
     * @throws Exception - if anything goes wrong aside from what is expected, return the function to the caller.
     */
    public void submitBuildToHelixALM(@NotNull Run<?, ?> run, FilePath workspace, EnvVars env,
                                      Launcher launcher, TaskListener listener,
                                      HALMTestReporterObject reportObj) throws Exception {
        try {
            String connID = reportObj.getHalmConnectionID();
            String projID = reportObj.getProjectID();
            String suiteID = Long.toString(reportObj.getAutomationSuiteID());

            HALMConnection conn = HALMGlobalConfig.get().getConnectionByNameOrID(connID);
            ConnectionInfo halmConnInfo = getConnectionInfo(conn);
            Client c = new Client(halmConnInfo);
            AuthInfoToken tok = c.getAuthToken(projID);

            // Gather build metadata.
            int buildNumber = run.getNumber();
            // Gather the build parameters.
            ParametersAction actions = run.getAction(ParametersAction.class);

            List<ParameterValue> params;
            if (actions != null) {
                params = actions.getParameters();
            }
            else {
                params = new ArrayList<>();
            }

            // Gather the files
            final String filesPath = env.expand(reportObj.getTestFilePattern());
            FilePath[] lstFiles = workspace.list(filesPath);

            ArrayList<String> lstPathStrs = new ArrayList<>();
            if (lstFiles.length > 0) {
                for (FilePath filePath : lstFiles) {
                    // We may want to refine this a bit...
                    // perhaps a partial success if some files are found?
                    if (!filePath.exists()) {
                        throw new FileNotFoundException("A test result file was not found.");
                    } else {
                        listener.getLogger().println("Found result file at " + filePath.getRemote());
                        lstPathStrs.add(filePath.getRemote());
                    }
                }
            } else {
                // We have an empty list, either because the entered path was wrong or there were no
                // files generated for one reason or another in the workspace.
                throw new FileNotFoundException("No test result files were generated by the build.");
            }

            boolean isApiKey;
            StandardCredentials creds = getCredentialsInfo(conn);
            String[] toks;
            if (creds instanceof StringCredentials) {
                toks = ((StringCredentials) creds).getSecret().getPlainText().split(":");
                isApiKey = true;
            }
            else {
                StandardUsernamePasswordCredentials cr = (StandardUsernamePasswordCredentials) creds;
                toks = new String[] { cr.getUsername(), cr.getPassword().getPlainText()};
                isApiKey = false;
            }
            listener.getLogger().println("Submitting build files to Helix ALM...");
            String buildResponse = workspace.act(new SubmitWithRemoteFiles(reportObj,
                    Integer.toString(buildNumber), run.getParent().getName(), env, params,
                    suiteID, tok.getAuthorizationHeader(), Long.toString(run.getQueueId()), toks[0], toks[1], isApiKey,
                    halmConnInfo.getUrl(), halmConnInfo.getPemCertContents(), lstPathStrs));
            if (buildResponse != null && !buildResponse.isEmpty()) {
                throw new Exception(buildResponse);
            }
            else {
                listener.getLogger().println("Build files submitted successfully.");
            }
        }
        catch (Exception e) {
            // Log errors somewhere; if listener is null, write to jenkins log, else write to console output.
            if (listener == null) {
                logger.log(Level.WARNING, "An error occurred when submitting the build: " + e.getMessage());
            }
            // Ensure the user can visibly tell something went wrong, assuming
            // that their build hasn't already failed prior to this step.
            run.setResult(Result.UNSTABLE);

            // Rethrow so caller can log errors.
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static final class SubmitWithRemoteFiles extends MasterToSlaveCallable<String, Throwable> {
        private static final long serialVersionUID = 1L;
        private final String projectID;
        private final String automationSuiteID;
        private final String testFileFormat;
        private final String testRunSet;
        private final long testRunSetID;
        private final String description;
        private final String branch;

        private final String buildNumber;

        private final String jenkinsProjectName;

        private final List<ParameterValue> params;

        private final EnvVars env;

        private final String authHeader;

        private final String queueID;

        private final String url;

        private final String user; // the first half of the credentials info we need

        private final String pass; // the second half of the credentials info we need

        private final boolean isApiKey;

        private final List<String> certs;

        private final List<String> lstPathStrs;

        public SubmitWithRemoteFiles(HALMTestReporterObject reportObj, String build, String projName, EnvVars e,
                                     List<ParameterValue> vals, String suiteID, String header, String qID,
                                     String u, String pas, boolean isKey, String apiUrl, List<String> crts,
                                     List<String> paths) throws Exception {
            projectID = reportObj.getProjectID();
            automationSuiteID = suiteID;
            testFileFormat = reportObj.getTestFileFormat();
            testRunSet = reportObj.getTestRunSet();
            testRunSetID = reportObj.getTestRunSetID();
            description = reportObj.getDescription();
            branch = reportObj.getBranch();
            buildNumber = build;
            jenkinsProjectName = projName;
            env = e;
            params = vals;
            authHeader = header;
            queueID = qID;
            url = apiUrl;
            certs = crts;
            user = u;
            pass = pas;
            isApiKey = isKey;
            lstPathStrs = paths;
        }

        @Override
        public String call() throws Throwable {

                // Gather the build properties we want to send to the client
                ArrayList<NameValuePair> props = new ArrayList<>();
                Platform p = env.getPlatform();
                props.add(new NameValuePair("os.type", p == null ? "" : determineOS(p)));
                for (String name : envVars) {
                    if (!env.get(name, "").isEmpty()) {
                        String displayName = StringUtils.capitalize(name.toLowerCase().replace("_", " "));
                        props.add(new NameValuePair(displayName, env.get(name, "")));
                    }
                }
                props.add(new NameValuePair("Environment Variables", env.toString()));

                IDLabelPair runSet = null;
                long setID = testRunSetID;
                if (setID > 0) {
                    runSet = new IDLabelPair(setID, getLabelOfTestRunSet(setID));
                }
                else {
                    String setName = testRunSet;
                    if (setName != null && !setName.isEmpty()) {
                        runSet = new IDLabelPair(-1, setName);
                    }
                }
                ArrayList<JenkinsBuildParameter> jParams = new ArrayList<>();
                for (ParameterValue param : params) {
                    String name = param.getName();
                    Object objValue = param.getValue();
                    if (!param.isSensitive()) {
                        JenkinsBuildParameterText textParam = new JenkinsBuildParameterText();
                        textParam.setText(objValue == null ? "" : objValue.toString());
                        textParam.setName(name);
                        jParams.add(textParam);
                    }
                }

            AutomationBuildRunConfigurationJenkins jConfig = new AutomationBuildRunConfigurationJenkins();
                jConfig.getJenkins().setBuildParameters(jParams);

                ReportContext rCxt = new ReportContext();
                rCxt.setReportFiles(lstPathStrs);
                rCxt.setReportFormatType(getTypeFromOrdinal(testFileFormat));
                ConnectionInfo halmConnInfo;
                if (isApiKey) {
                    halmConnInfo = new ConnectionInfo(url, new AuthInfoAPIKey(user, pass), certs);
                }
                else {
                    halmConnInfo = new ConnectionInfo(url, new AuthInfoBasic(user, pass), certs);
                }
                HelixALMSuiteContext halmCxt = new HelixALMSuiteContext();
                halmCxt.setRestAPIConnectionInfo(halmConnInfo);
                halmCxt.setHelixALMProjectID(projectID);
                halmCxt.setHelixALMSuiteID(automationSuiteID);

                BuildMetadata metadata = new BuildMetadata();
                metadata.setPendingRunID(queueID);
                metadata.setSourceOverride("Jenkins Plugin");
                if (branch != null && !branch.isEmpty())
                    metadata.setBranch(branch);
                if (description != null && !description.isEmpty())
                    metadata.setDescription(description);
                if (runSet != null)
                    metadata.setTestRunSet(runSet);

                metadata.setExternalURL(env.get("BUILD_URL"));
                metadata.setProperties(props);
                metadata.setRunConfigurationInfo(jConfig);

                BuildSubmitter submitter = new BuildSubmitter(buildNumber,
                        rCxt, halmCxt, metadata);
                SubmitAutomationBuildResponse response = submitter.submitAutomationBuild();
                if (response.isError()){
                    return response.getErrorMessage();
                }
                return null;
        }
    }
}

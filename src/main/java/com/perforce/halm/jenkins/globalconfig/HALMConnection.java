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

package com.perforce.halm.jenkins.globalconfig;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.perforce.halm.jenkins.HALMTestReporterCommon;
import com.perforce.halm.reportingtool.APIAuthType;
import com.perforce.halm.rest.*;
import com.perforce.halm.rest.responses.ProjectListResponse;
import com.perforce.halm.rest.types.VersionInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import feign.FeignException;
import hudson.Extension;
import hudson.XmlFile;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.XStream2;
import jenkins.model.Jenkins;
import okhttp3.HttpUrl;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Global setting that defines a single Helix ALM REST API connection
 */
@SuppressWarnings("FieldMayBeFinal")
public class HALMConnection extends AbstractDescribableImpl<HALMConnection> implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * A generated UUID for this connection.
     */
    private String connectionUUID;

    /**
     * Unique name for this connection. Primarily exists for use w/ Pipelines where we need to refer to this
     * by a simple, easy to reference name. For Freestyle the user is going to pick this name out of a dropdown.
     */
    private String connectionName;

    /**
     * Helix ALM REST API address (ex: https://localhost:8443 )
     */
    private String halmAPIAddress;

    /**
     * Helix ALM REST API credentials type.
     */
    private APIAuthType credentialType = APIAuthType.basic;

    /**
     * 'Credentials' plugin will store the credentials securely. This is the ID of the credentials being stored
     * by that plugin.
     */
    private String credentialsID;

    /**
     * Contains the optional connection properties. May be null.
     */
    private OptionalConnectionProps optionalConnectionProps;

    /**
     * Stored list of SSL certificates.
     */
    private List<String> acceptedSSLCertificates;

    private static final Logger logger = Logger.getLogger("jenkins.HALMConnection");

    /**
     * Constructor.
     *
     * @param connectionUUID - Unique connection ID
     * @param connectionName - the (hopefully unique) connection name.
     * @param halmAPIAddress - Helix ALM API REST API address
     * @param credentialsID - ID of the credentials
     */
    @DataBoundConstructor
    public HALMConnection(String connectionUUID,
                          String connectionName,
                          String halmAPIAddress,
                          String credentialsID) {
        if (StringUtils.isEmpty(connectionUUID)) {
            this.connectionUUID = UUID.randomUUID().toString();
        }
        else {
            this.connectionUUID = connectionUUID;
        }
        this.connectionName = connectionName;
        this.halmAPIAddress = halmAPIAddress;
        this.credentialsID = credentialsID;

        loadAcceptedCertificates();
    }

    /**
     * @return Unique, unfriendly, connection ID
     */
    public String getConnectionUUID() {
        return connectionUUID;
    }

    /**
     * @return Friendly connection name. May be empty. Maybe a duplicate of another connection. Prefer this (if it exists) when generating Pipeline syntax.
     */
    public String getConnectionName() {
        return connectionName;
    }

    /**
     * @param connectionName New connection ID to set
     */
    @DataBoundSetter
    public final void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
    }

    /**
     * @return Helix ALM REST API address
     */
    @SuppressWarnings("unused") // This is used by Jenkins, but IntelliJ will flag as unused
    public String getHalmAPIAddress() {
        return halmAPIAddress;
    }

    /**
     * @param halmAPIAddress Helix ALM REST API address
     */
    @DataBoundSetter
    public final void setHalmAPIAddress(String halmAPIAddress) {
        this.halmAPIAddress = halmAPIAddress;
    }

    /**
     * @return Credential type, for use w/ setting the global config dropdown
     */
    @SuppressWarnings("unused") // This is used by Jenkins, but IntelliJ will flag as unused
    public String getCredentialTypeValue() {
        return getAuthTypeValue(this.credentialType);
    }

    /**
     * @param credentialType Credential type, as set on the global config dropdown.
     */
    @DataBoundSetter
    public final void setCredentialTypeValue(String credentialType) {
        this.setCredentialType(getAuthTypeFromValue(credentialType));
    }

    /**
     * @return Type of credentials (basic vs API Key)
     */

    public APIAuthType getCredentialsType() {
        return this.credentialType;
    }

    /**
     * @param credentialType Type of credentials (basic vs API Key)
     */
    public void setCredentialType(APIAuthType credentialType) {
        this.credentialType = credentialType;
    }

    /**
     * @return ID of the credentials as stored in the Credentials plugin
     */
    @SuppressWarnings("unused") // This is used by Jenkins, but IntelliJ will flag as unused
    public String getCredentialsID() {
        return credentialsID;
    }

    /**
     * @param credentialsID  ID of the credentials as stored in the Credentials plugin
     */
    @DataBoundSetter
    public final void setCredentialsID(String credentialsID) {
        this.credentialsID = credentialsID;
    }

    /**
     * This is left for legacy support when 'AcceptSSLCertificates' was a top-level property.
     * Old configurations may be saved with this setting and they need this function to
     * self-upgrade the configuration.
     *
     * @param acceptSSLCertificates Are we accepting SSL certificates
     */
    @DataBoundSetter
    public final void setAcceptSSLCertificates(boolean acceptSSLCertificates) {
        if (this.optionalConnectionProps == null) {
            this.optionalConnectionProps = new OptionalConnectionProps();
        }
        this.optionalConnectionProps.acceptSSLCertificates = acceptSSLCertificates;
    }

    /**
     * This is here due to what looks like an odd bug in Jenkins? I would expect
     * {@link OptionalConnectionProps} to be invoked to retrieve this information, however
     * the setting did not appear to be populated in the UI until I added this.
     *
     * @return Returns the current setting for 'acceptSSLCertificates'
     */
    public final boolean getAcceptSSLCertificates() {
        return this.optionalConnectionProps != null && this.optionalConnectionProps.acceptSSLCertificates;
    }

    /**
     * This governs if the 'optional' checkbox is checked or not.
     *
     * @return Is the optional connection props set?
     */
    public boolean getOptionalConnectionPropsIsSet() {
        return optionalConnectionProps != null;
    }

    /**
     * @return The currently set optional properties object. This may be null
     */
    public OptionalConnectionProps getOptionalConnectionProps() {
        return optionalConnectionProps;
    }

    /**
     * Sets the optional connection properties
     * @param optionalConnectionProps Optional connection properties to set.
     */
    @DataBoundSetter
    public void setOptionalConnectionProps(OptionalConnectionProps optionalConnectionProps) {
        this.optionalConnectionProps = optionalConnectionProps;
    }

    /**
     * @return List of accepted SSL certificates.
     */
    public List<String> getAcceptedSSLCertificates() {
        if (acceptedSSLCertificates != null) {
            return new ArrayList<>(acceptedSSLCertificates);
        }
        else {
            // if the value is null, return an empty ArrayList.
            return new ArrayList<>();
        }
    }

    /**
     * @param acceptedSSLCertificates Set list of accepted SSL certificates
     */
    @DataBoundSetter
    public void setAcceptedSSLCertificates(List<String> acceptedSSLCertificates) {
        this.acceptedSSLCertificates = new ArrayList<>(acceptedSSLCertificates);
    }

    /**
     * Loads the certificates that are stored on disk
     */
    private void loadAcceptedCertificates() {
        // If and only if we're dealing with a secure URL, we check the certificates.
        if (this.halmAPIAddress != null && this.halmAPIAddress.startsWith("https://")) {
            this.acceptedSSLCertificates = null;

            boolean loadedCerts = loadAcceptedCertsFromFile(this.connectionUUID + ".xml");
            if (!loadedCerts) {
                try {
                    String altFileName = DescriptorImpl.encodeInputAsValidFilename(this.connectionName) + ".xml";

                    loadedCerts = loadAcceptedCertsFromFile(altFileName);

                    if (loadedCerts) {
                        moveCertificateByNameToUUID(altFileName, this.connectionUUID + ".xml");
                    }
                }
                catch (UnsupportedEncodingException e) {
                    String errMessage = String.format("An error occurred when attempting to load the stored Helix ALM " +
                        "connection certificates by connection name [%s]: %s", this.connectionName, e.getMessage());
                    logger.warning(errMessage);
                }
            }
        }
    }

    /**
     * Loads the specified certificate storage file from disk.
     *
     * @param fileName Name of the file to load
     * @return True if the file was loaded, false otherwise.
     */
    private boolean loadAcceptedCertsFromFile(String fileName) {
        boolean loadedCerts = false;

        File certFile = new File(Jenkins.get().getRootDir(), fileName);
        if (certFile.exists()) {
            XStream2 certsIn = new XStream2();
            XmlFile fileIn = new XmlFile(certsIn, certFile);

            List<String> tmpAcceptedCerts = new ArrayList<>();
            try {
                Object fileContents = fileIn.read();
                if (fileContents instanceof List<?>) {
                    for (Object obj : (List<?>) fileContents) {
                        if (obj instanceof String) {
                            tmpAcceptedCerts.add((String) obj);
                        }
                        else {
                            String errMessage = String.format("Attempting to read saved Helix ALM connection information failed. " +
                                "Contents of the certificate storage file at %s where not as expected.", fileName);
                            logger.warning(errMessage);
                        }
                    }

                    this.acceptedSSLCertificates = tmpAcceptedCerts;
                    loadedCerts = true;
                }
                else {
                    String errMessage = String.format("Attempting to read saved Helix ALM connection information failed. " +
                        "Contents of the certificate storage file at %s where not as expected.", fileName);
                    logger.warning(errMessage);
                }
            }
            catch (IOException | SecurityException ex) {
                String errMessage = String.format("An error occurred when reading saved Helix ALM connection information " +
                    "from %s. %s", fileName, ex.getMessage());
                logger.warning(errMessage);
            }
        }


        return loadedCerts;
    }

    /**
     * Moves the specified file to the new file name.
     *
     * @param connNameFileName Name of the source file
     * @param newFileName Name of the destination file
     */
    private void moveCertificateByNameToUUID(String connNameFileName, String newFileName) {
        File parentFile = Jenkins.get().getRootDir();
        File srcFile = new File(parentFile, connNameFileName);
        File dstFile = new File(parentFile, newFileName);

        try {
            Files.move(srcFile.toPath(), dstFile.toPath());
        }
        catch (IOException e) {
            String errMessage = String.format("An error occurred when attempting to move the Helix ALM connection " +
                "certificates file from %s to %s. %s", connNameFileName, newFileName, e.getMessage());
            logger.warning(errMessage);
        }
    }

    /**
     * Converts an APIAuthType enum to a string value used in the UI for the dropdown list.
     *
     * @param authType - Authorization Type to convert
     * @return String representation of a number that maps to the AuthType's ordinal value.
     */
    private static String getAuthTypeValue(APIAuthType authType) {
        return Integer.toString(authType.ordinal());
    }

    /**
     * Converts an AuthTypeValue from {@link HALMConnection#getAuthTypeValue(APIAuthType)} back to the enum value.
     * @param value Value to convert to an AuthType
     * @return APIAuthType value
     */
    private static APIAuthType getAuthTypeFromValue(String value) {
        int ordinalType = Integer.parseInt(value);
        return APIAuthType.values()[ordinalType];
    }


    /**
     * Abstract base class for results, provides common error handling.
     */
    public abstract static class AbstractResult {
        /**
         * If an error occurs while parsing the authorization info, this will be set to a message indicating
         * what the problem was.
         */
        public String error;

        /**
         * Checks if the result indicates an error occurred. Check {@link AbstractResult#getErrorMessage()} for an
         * error message.
         *
         * @return True if there was an error,
         */
        public final boolean isError() {
            return isErrorInternal() || error != null;
        }

        /**
         * Any relevant error message. If no error message exists, a default one is returned.
         *
         * @return Any relevant error message. If no error message exists, a default one is returned.
         */
        public String getErrorMessage() {
            String errMsg = error;
            if (error == null || error.isEmpty()) {
                //wordsmith_ptv
                errMsg = "An unknown error occurred while determining Helix ALM REST API authorization information.";
            }
            return errMsg;
        }

        /**
         * Implementer can extend isError safely by implementing isErrorInternal.
         *
         * @return True indicates the implementor thinks this result should be a failure.
         */
        protected boolean isErrorInternal() {
            return false;
        }
    }

    /**
     * Authorization information result
     */
    public static class AuthInfoResult extends AbstractResult{
        /**
         * Authorization info. This may be null if error is set.
         */
        public IAuthInfo authInfo;

        /**
         * @inheritDoc
         */
        @Override
        public boolean isErrorInternal() {
            return authInfo == null;
        }
    }

    /**
     * Credentials information result
     */
    public static class CredentialDetailsResult extends AbstractResult {
        /**
         * Username or User ID
         */
        public String userID = "";

        /**
         * User password or user secret
         */
        public String userSecret = "";
    }


    /**
     * Retrieves credential info (username // password)
     *
     * @return A CredentialDetailsResult object with either the credentials, or an error.
     */
    public CredentialDetailsResult getCredentialDetails() {
        return getCredentialDetails(this.getCredentialsID());
    }

    /**
     * Retrieves credential info (username // password)
     *
     * @param credentialsID Jenkins Credentials ID
     * @return A CredentialDetailsResult object with either the credentials, or an error.
     */
    public static CredentialDetailsResult getCredentialDetails(final String credentialsID) {
        // Extract the type of credentials later when we go to create our connection object.
        // We support StandardUsernamePasswordCredentials and StringCredentials (secret text)
        // We have the Plain Credentials plugin as a required plugin.
        // https://plugins.jenkins.io/plain-credentials/
        CredentialDetailsResult result = new CredentialDetailsResult();
        StandardCredentials credentials = HALMTestReporterCommon.getCredentialsFromId(credentialsID);
        if (credentials != null) {

            if (credentials instanceof StringCredentials) {
                StringCredentials stringCredentials = (StringCredentials) credentials;
                String[] tokens = stringCredentials.getSecret().getPlainText().split(":", 2);
                if (tokens.length == 2) {
                    result.userID = tokens[0];
                    result.userSecret = tokens[1];
                } else {
                    //wordsmith_ptv
                    result.error = "Invalid format for secret text credentials. Secret text credentials must " +
                        "have a user ID and user secret separated by a ':' character.";
                }
            } else if (credentials instanceof StandardUsernamePasswordCredentials) {
                StandardUsernamePasswordCredentials usernamePasswordCredentials = (StandardUsernamePasswordCredentials) credentials;
                result.userID  = usernamePasswordCredentials.getUsername();
                result.userSecret = usernamePasswordCredentials.getPassword().getPlainText();
            } else {
                //wordsmith_ptv
                result.error = "Credentials must be either 'Username with password' or 'Secret text'.";
            }
        }
        else {
            //wordsmith_ptv
            result.error = String.format("Could not find credentials with ID %s", credentialsID);
        }

        return result;
    }

    /**
     * We need to check for a variety of conditions while parsing the credentials. This handles all the odd edge
     * cases and ensures we handle & return appropriate errors.
     *
     * @return An authorization info result which either contains the auth info, or an error message indicating failure.
     */
    public AuthInfoResult getAuthInfo() {
        return getAuthInfo(this.credentialType, this.credentialsID);
    }

    /**
     * We need to check for a variety of conditions while parsing the credentials. This handles all the odd edge
     * cases and ensures we handle & return appropriate errors.
     *
     * @param authType Authorization type (API Key vs Basic)
     * @param credentialsID Jenkins Credentials ID
     * @return An authorization info result which either contains the auth info, or an error message indicating failure.
     */
    protected static AuthInfoResult getAuthInfo(APIAuthType authType, final String credentialsID) {
        AuthInfoResult authInfoResult = new AuthInfoResult();

        CredentialDetailsResult credentialDetailsResult = getCredentialDetails(credentialsID);
        if (credentialDetailsResult.isError()) {
            authInfoResult.error = credentialDetailsResult.getErrorMessage();
        }
        else {
            if (authType == APIAuthType.basic) {
                authInfoResult.authInfo = new AuthInfoBasic(credentialDetailsResult.userID, credentialDetailsResult.userSecret);
            }
            else if (authType == APIAuthType.apiKey) {
                authInfoResult.authInfo = new AuthInfoAPIKey(credentialDetailsResult.userID, credentialDetailsResult.userSecret);
            }
            else {
                //wordsmith_ptv
                authInfoResult.error = "Unsupported Helix ALM API authorization type selected.";
            }
        }

        return authInfoResult;
    }

    /**
     * Optional connection properties
     */
    public static final class OptionalConnectionProps implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
        * Should this connect accept // download the first set of certificates it encounters?
        */
        private boolean acceptSSLCertificates;

        /**
         * Constructor.
         */
        @DataBoundConstructor
        public OptionalConnectionProps() {
           // Don't set the optional settings via the constructor, instead rely on jenkins to call the appropriate
           // setXYZ DataBoundSetter functions. This makes upgrading between configurations easier.
        }

        /**
         * @return Are SSL certificates being accepted regardless of their 'validness'?
         */
        public boolean getAcceptSSLCertificates() {
            return acceptSSLCertificates;
        }

        /**
         * Sets whether we should accept invalid SSL certificates.
         * @param acceptSSLCertificates True indicates we should accept invalid SSL certificates
         */
        @DataBoundSetter
        public void setAcceptSSLCertificates(boolean acceptSSLCertificates) {
            this.acceptSSLCertificates = acceptSSLCertificates;
        }
    }

    /**
     * Used for validating the HALMConnection > config.jelly
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<HALMConnection> {

        /**
         * Constructor
         */
        public DescriptorImpl() {
            super(HALMConnection.class);
            load();
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Helix ALM REST API Server";
        }

        /**
         * Fills the 'Credentials' dropdown.
         *
         * @param context - used to determine if the user can get a list of users
         * @param remote - the remote server in question
         * @return Returns the list of items to put into the Credentials dropdown.
         */
        @SuppressWarnings("unused") // This is used by Jenkins, but IntelliJ will flag as unused
        public ListBoxModel doFillCredentialsIDItems(@AncestorInPath final Item context,
                                                     @QueryParameter final String remote) {
            Jenkins j = Jenkins.get();

            if ((context == null && !j.hasPermission(Jenkins.ADMINISTER)) ||
                (context != null && !context.hasPermission(Item.EXTENDED_READ))) {
                // User probably doesn't have permission to get the list of users for some reason
                return new StandardListBoxModel();
            }

            List<DomainRequirement> domainRequirements;
            if (remote == null) {
                domainRequirements = Collections.emptyList();
            } else {
                domainRequirements = URIRequirementBuilder.fromUri(remote.trim()).build();
            }

            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeAs(ACL.SYSTEM, context, StringCredentials.class, domainRequirements)
                    .includeAs(ACL.SYSTEM, context, StandardUsernamePasswordCredentials.class, domainRequirements);
        }

        /**
         * Populates the 'credentialType' dropdown with appropriate values.
         *
         * @return Credential Type dropdown list values.
         */
        @SuppressWarnings("unused") // This is used by Jenkins, but IntelliJ will flag as unused
        public ListBoxModel doFillCredentialTypeValueItems() {
            ListBoxModel items = new ListBoxModel();

            for (APIAuthType authType : APIAuthType.values()) {

                items.add(getAuthTypeDescription(authType),
                    HALMConnection.getAuthTypeValue(authType));
            }

            return items;
        }

        /**
         * Validates that the address provided by the user is valid. This is called when
         * the HALM address field loses focus after text is entered. It is an ajax call
         * defined in the config.jelly which calls this function.
         *
         * The @POST just above the function is recommended in the dev docs for Jenkins. It is not required,
         * but is included for security concerns:
         * https://www.jenkins.io/doc/developer/security/form-validation/
         *
         * @param halmAPIAddress HALM REST API address
         * @return Form validation status.
         */
        @POST
        public FormValidation doCheckAddress(@QueryParameter("value") final String halmAPIAddress) {
            // Before anything else is done - check the URL the user entered.
            if (!halmAPIAddress.startsWith("http://") && !halmAPIAddress.startsWith("https://")) {
                return FormValidation.error("The REST API address must start with http:// or https://.");
            }
            return FormValidation.ok();
        }

        /**
         * Verifies that the connection name is not blank.
         *
         * @param connectionName Connection name to verify
         * @return FormValidation.Ok if the connection name is not blank.
         */
        @POST
        public FormValidation doCheckConnectionName(@QueryParameter("connectionName") final String connectionName) {
            if (StringUtils.isBlank(connectionName)) {
                return FormValidation.error("The connection name cannot be empty");
            }
            return FormValidation.ok();
        }

        /**
         * Checks that the credentials ID has been set.
         *
         * @param credentialsID The selection in the credentials dropdown
         * @return Returns Ok if there is something selected, an error otherwise.
         */
        @POST
        public FormValidation doCheckCredentialsID(@QueryParameter("credentialsID") final String credentialsID) {
            if (StringUtils.isBlank(credentialsID)) {
                return FormValidation.error("Credentials must be selected.");
            }
            return FormValidation.ok();
        }

        /**
         * Validates that the provided HALM connection information settings are valid. This is called when the
         * 'Test Connection' button is pressed.
         *
         * The @POST just above the function is recommended in the dev docs for Jenkins. It is not required,
         * but is included for security concerns:
         * https://www.jenkins.io/doc/developer/security/form-validation/
         *
         * @param connectionUUID the connection's UUID, if it exists.
         * @param connectionName the connection's name from the field
         * @param halmAPIAddress HALM REST API address
         * @param credentialTypeValue Basic vs Auth from the dropdown
         * @param credentialsID Credential ID from Credentials plugin
         * @param acceptSSLCertificates Should this accept invalid certs?
         * @return Form validation status.
         */
        @POST
        public FormValidation doTestConnection(@QueryParameter("connectionUUID") final String connectionUUID,
                                               @QueryParameter("connectionName") final String connectionName,
                                               @QueryParameter("halmAPIAddress") final String halmAPIAddress,
                                               @QueryParameter("credentialTypeValue") final String credentialTypeValue,
                                               @QueryParameter("credentialsID") final String credentialsID,
                                               @QueryParameter("acceptSSLCertificates") final boolean acceptSSLCertificates) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            // Initial parameter validation
            if (StringUtils.isBlank(connectionName) ||
                StringUtils.isBlank(halmAPIAddress) ||
                StringUtils.isBlank(credentialsID)) {
                return FormValidation.error("Connection name, REST API address, and Credentials cannot be empty.");
            }

            try {
                HttpUrl urlObj;
                String finalURL;

                String errMsg_ConnectionFailure = String.format("Cannot connect to the REST API Server at %s. ", halmAPIAddress);

                // Using OkHttp3 to build the full URL to the REST API.
                try {
                    // HttpUrl.parse will throw an exception
                    // if it can't actually connect to the indicated address
                    // when .build() is called.

                    urlObj = HttpUrl.parse(halmAPIAddress)
                            .newBuilder()
                            .build();
                    finalURL = urlObj.toString();
                }
                catch (Exception e) {
                    logger.log(Level.INFO, e.getMessage());
                    return FormValidation.error(errMsg_ConnectionFailure);
                }

                // Build the connection object
                APIAuthType authType = getAuthTypeFromValue(credentialTypeValue);
                AuthInfoResult authInfoResult = getAuthInfo(authType, credentialsID);
                if (authInfoResult.isError()) {
                    logger.log(Level.INFO, authInfoResult.getErrorMessage());
                    return FormValidation.error(authInfoResult.getErrorMessage());
                }

                ConnectionInfo connectionInfo = new ConnectionInfo(finalURL, authInfoResult.authInfo);

                // Lets use the halm-rest-client to check to see the destination host exists & looks like the REST API
                Client tmpClient = new Client(connectionInfo);
                Exception tmpEx = tmpClient.doesServerExist();
                if (tmpEx != null) {
                    String errMessageLog = errMsg_ConnectionFailure + tmpEx.getMessage();
                    logger.log(Level.INFO, errMessageLog);

                    String errMessageUI = errMsg_ConnectionFailure;
                    if (tmpEx instanceof feign.FeignException) {
                        feign.FeignException feinEx = (feign.FeignException) tmpEx;
                        if (feinEx.status() == -1 ) {
                            errMessageUI += "\n\nException message: " + feinEx.getMessage();
                        } else {
                            errMessageUI += String.format(" Server returned HTTP status code %d. See the Jenkins log for details.", feinEx.status());
                        }
                    }
                    return FormValidation.error(errMessageUI);
                }

                // If this is an HTTPS connection, we need to determine the certificate status for the connection
                if (urlObj.isHttps()) {
                    // See if we've already got certs.
                    List<String> acceptedCerts;
                    if (connectionUUID != null && !connectionUUID.isEmpty()) {
                        acceptedCerts = HALMGlobalConfig.get()
                                .getConnectionByNameOrID(connectionUUID)
                                .getAcceptedSSLCertificates();

                        connectionInfo.setPemCertContents(acceptedCerts);
                    }

                    // Figure out the certificate status
                    CertificateInfo certificateInfo = CertUtils.getServerCertStatus(connectionInfo);
                    List<String> certs = null;
                    switch (certificateInfo.getStatus()) {
                        case INVALID:
                            return FormValidation.error("The SSL certificate used by the specified REST API server " +
                                    "is invalid and cannot be used.");
                        case INVALID_DOWNLOADABLE:
                            // Check if they're accepting SSL certificates before getting them.
                            if (acceptSSLCertificates) {
                                certs = certificateInfo.getPemCertificates();
                                if (certs == null || certs.isEmpty()) { // paranoid double-check, if a secure connection did
                                    // not return certs, we should let the user know.
                                    return FormValidation.error("The SSL certificate used by the specified REST API server " +
                                            "is invalid and cannot be used.");
                                }
                            } else {
                                return FormValidation.warning("The SSL certificate used by the specified REST API server " +
                                        "is invalid. To use it anyway to connect to this server, select " +
                                        "'Accept SSL certificates' and try again.");
                            }
                            break;
                        case VALID:
                        case TRUSTED:
                            break;
                    }

                    // Download Certs if INVALID_DOWNLOADABLE & we were told to update the connectionInfo w/certs.
                    // We already checked if acceptSSLCertificates is true, so no need to check it again - if we reached this far.
                    if (acceptSSLCertificates && certificateInfo.getStatus() == CertificateStatus.INVALID_DOWNLOADABLE) {
                        connectionInfo.setPemCertContents(certs);
                        XStream2 certsOut = new XStream2();
                        XmlFile fileOut;
                        if (connectionUUID != null && !connectionUUID.isEmpty()) {
                            fileOut = new XmlFile(certsOut, new File(Jenkins.get().getRootDir(),
                                    connectionUUID + ".xml"));
                        } else {
                            fileOut = new XmlFile(certsOut, new File(Jenkins.get().getRootDir(),
                                    encodeInputAsValidFilename(connectionName) + ".xml"));
                        }
                        fileOut.write(certs); // this will update an existing certs file if the cert(s) changed
                    }
                }

                // If we have gotten this far we can make a REST API client
                Client c = new Client(connectionInfo);

                // Check REST API version - ensure it is 2022.2.0+
                VersionInfo versions = c.getVersions();

                int[] minimumVersion = { 2022, 2, 0 };
                FormValidation validationErr = checkRESTAPIVersion(versions, minimumVersion);
                if (validationErr != null) {
                    return validationErr;
                }

                validationErr = checkHALMServerConnection(versions);
                if (validationErr != null) {
                    return validationErr;
                }

                // 2022.2.0 and later versions are compatible with the Jenkins plugin,
                // so if we've made it this far then we have a valid REST API.

                //  Check credentials work via retrieving a token.
                // We will retrieve the first project from the list
                // the Jenkins user should be able to log in to this project on the ALM side.
                try {
                    ProjectListResponse projectListResponse = c.getProjects();
                    if (projectListResponse.isError()) {
                        return FormValidation.error("Could not retrieve Helix ALM projects using the REST API. " + projectListResponse.getErrorMessage());
                    }
                    else if (projectListResponse.getProjects().isEmpty()) {
                        return FormValidation.error("The specified user does not have permission to " +
                                "access any Helix ALM projects using the REST API.");
                    }
                }
                catch (Exception e) {
                    // 401 & 403 errors from REST can be obtained via bad errors
                    // or licensing, 500 errors are from bad API Keys.
                    if (e instanceof FeignException.Unauthorized ||
                            e instanceof FeignException.Forbidden ||
                            e instanceof FeignException.InternalServerError) {
                        return FormValidation.error("Cannot connect to the REST API server. " +
                                "The specified credentials are invalid.");
                    }
                    else { // re-throwing until we decide otherwise
                        logger.log(Level.INFO, e.getMessage());
                        throw e;
                    }
                }

                // If we've made it all the way here, we have a connection.
                return FormValidation.ok("Connection successful.");
            } catch (Exception ex) {
                logger.warning("Unknown error while testing Helix ALM connection: " + getExceptionForLogging(ex));
                return FormValidation.error("Unknown error: " + ex.getMessage());
            }
        }

        /**
         * Returns an encoded version of the given string that will be a valid filename.
         *
         * @param input The input string to encode
         * @return See description
         * @throws UnsupportedEncodingException
         */
        public static String encodeInputAsValidFilename(String input) throws UnsupportedEncodingException {
            return URLEncoder.encode(input, "UTF-8")
                    .replace("+", "%20")
                    .replace(".", "%2E")
                    .replace("*", "%2A");
        }

        /**
         * Converts the exception into a stack trace we can log.
         *
         * @param ex Exception we want to log
         * @return String containing the stack trace of the error.
         */
        public static String getExceptionForLogging(Exception ex) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            return sw.toString();
        }

        /**
         * Compares a string based version (ex: "2022.2.0" vs a numeric based minimum version, ex: [2022, 2, 0] to determine
         * if the string based version is equal to, or newer than, the minimum version.
         *
         * @param minimumVersion Minimum supported version, as a numeric array: Ex: [2022, 2, 0]
         * @param versionToCheck Current version, as text. Ex: "2022.2.0"
         * @return FormValidation.OK on success, a FormValidation error otherwise.
         */
        private FormValidation compareVersions(int[] minimumVersion, String versionToCheck) {
            FormValidation result = null;

            try {
                String[] splitVersion = versionToCheck.split("\\.");
                if (splitVersion.length >= minimumVersion.length) {
                    for (int i = 0; i < minimumVersion.length && result == null; i++) {
                        int curMinVersion = minimumVersion[i];
                        int curVersion = Integer.parseInt(splitVersion[i]);

                        if (curVersion < curMinVersion) {
                            String minVersionText = StringUtils.join(ArrayUtils.toObject(minimumVersion), ".");
                            result = FormValidation.error(String.format("Cannot connect to the specified Helix ALM REST " +
                                "API Server because it is an old version not supported by the Helix ALM Test Case " +
                                "Management plugin. The REST API version must be %s or later.", minVersionText));
                        }
                    }
                }
            } catch (NumberFormatException ex) {
                String errorMessage = "An error occurred when attempting to determine the Helix ALM REST API Server version. ";
                logger.warning(errorMessage + getExceptionForLogging(ex));
                result = FormValidation.error( errorMessage+ ex.getMessage());
            }

            return result;
        }

        /**
         * Uses the REST API version info to determine compatibility.
         *
         * @param inVersionInfo - REST API version information.
         * @param minimumVersion - Minimum supported REST API version
         * @return null on success, a FormValidation error otherwise.
         */
        private FormValidation checkRESTAPIVersion(VersionInfo inVersionInfo, int[] minimumVersion) {
            FormValidation result = null;

            // Verify the REST API version
            if (!inVersionInfo.getRESTAPIServer().equals(".")) {
                // We don't need to bother checking the version if we are connecting to a debug Helix ALM REST API server.
                // This implies we are connecting to a dev box.
                result = compareVersions(minimumVersion, inVersionInfo.getRESTAPIServer());
            }

            return result;
        }

        /**
         * Checks to see if the version information indicates we can connect to the Helix ALM Server backing the
         * REST API server.
         *
         * @param inVersionInfo REST API version information
         * @return null on success, a FormValidation error otherwise.
         */
        private FormValidation checkHALMServerConnection(VersionInfo inVersionInfo) {
            FormValidation result = null;

            // Now, while we are here, lets verify that the REST API is actually connected to the HALM Server
            String serverVersion = inVersionInfo.getHALMServer();
            String[] splitServer = serverVersion.split("\\.");
            if (serverVersion.equals("<unknown>") || splitServer[0].equals(serverVersion)) {
                // The REST API returns "<unknown>" if it cannot connect to the Helix ALM Server
                // Just in case this error message changes, we are also going to check to see if we failed to split based on '.'
                result = FormValidation.error("Connected to the Helix ALM REST API Server, but it cannot connect to the " +
                    "Helix ALM Server. Make sure the Helix ALM Server is running and the REST API Server is configured " +
                    "to connect to it.");
            }

            return result;
        }

        /**
         * Returns the text description for an API Authorization Type enum
         *
         * @param authType AuthType to convert
         * @return Text description for the auth type.
         */
        private static String getAuthTypeDescription(APIAuthType authType) {
            String displayName = authType.toString();
            switch (authType) {
                case apiKey:
                    displayName = "API Key";
                    break;
                case basic:
                    displayName = "Username & Password";
                    break;
                default:
                    assert false : String.format("Missing description for AuthType enum %s - %d", displayName,
                            authType.ordinal());
                    break;
            }
            return displayName;
        }
    }
}

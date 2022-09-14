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

import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;

import javax.inject.Inject;
import java.security.InvalidParameterException;
import java.util.*;

@Extension
public class HALMGlobalConfig extends GlobalConfiguration {
    private static final String CONNECTION_NAME = "connectionName";
    private static final String API_ADDRESS = "halmAPIAddress";
    private static final String CREDENTIALS_ID = "credentialsID";

    private List<HALMConnection> connections = new ArrayList<>();

    @Inject
    public HALMGlobalConfig() {
        load();
    }

    public HALMGlobalConfig(List<HALMConnection> connections) {
        this.connections = new ArrayList<>(connections);
    }

    @Override
    public boolean configure(StaplerRequest req,
                             JSONObject json)
            throws FormException {
        boolean clearConnections = json.get("connections") == null && !connections.isEmpty();
        if (clearConnections) {
            json.put("connections", new JSONObject());
        }

        // This throws an exception if one of the entries fails validation.
        validateConnections(json);

        req.bindJSON(this, json);
        save();
        if (clearConnections) {
            connections.clear();
        }

        return super.configure(req, json);
    }

    /**
     * Validates that the names of each connection are unique.
     * @param jObj - the JSON object to check for errors.
     * @throws FormException Throws a FormException if the connection fails validation.
     */
    private void validateConnections(JSONObject jObj) throws FormException {
        Set<String> encounteredNames = new HashSet<>();

        Object connectionsObj = jObj.get("connections");
        String rootPath = "connections";
        // sanity check to make sure we got the right thing
        if (connectionsObj instanceof JSONArray) {
            JSONArray jConnections = (JSONArray)connectionsObj;
            for (int i = 0; i < jConnections.size(); i++) {
                JSONObject jConnection = jConnections.getJSONObject(i);
                String formPath = String.format("%s.%d.", rootPath, i);

                validateConnection(jConnection, formPath, encounteredNames);
            }
        }
        else if (connectionsObj instanceof JSONObject) {
            JSONObject jConnection = (JSONObject) connectionsObj;
            String formPath = rootPath + '.';

            validateConnection(jConnection, formPath, encounteredNames);
        }
        else {
            throw new FormException("Unrecognized parameter type.", "connections");
        }
    }

    /**
     * Validates a single Helix ALM connection.
     *
     * @param jConnection Connection to validate
     * @param formPath Root form path to use for ForExceptions
     * @param encounteredNames Set of connection names we have encountered.
     * @throws FormException Throws a FormException if the connection fails validation.
     */
    private void validateConnection(JSONObject jConnection, String formPath, Set<String> encounteredNames) throws FormException {
        // This throws an exception if any of the required properties are missing or malformed.
        validateRequiredProperties(jConnection, formPath);

        String connectionName = jConnection.getString(CONNECTION_NAME);
        if (encounteredNames.contains(connectionName)) {
            throw new FormException("The Helix ALM connection name must be unique.", formPath + CONNECTION_NAME);
        }
        encounteredNames.add(connectionName);
    }

    /**
     * Ensures that the required properties on a connection are present before allowing it to be saved.
     *
     * @param jObj Connection object
     * @throws FormException Throws a FormException if the connection fails validation.
     */
    private void validateRequiredProperties(JSONObject jObj, String formPath) throws FormException {
        if (StringUtils.isBlank(jObj.getString(CONNECTION_NAME))) {
            throw new FormException("The Helix ALM connection name cannot be empty.", formPath + CONNECTION_NAME);
        }

        String apiAddress = jObj.getString(API_ADDRESS);
        if (StringUtils.isBlank(apiAddress) || (!apiAddress.startsWith("http://") && !apiAddress.startsWith("https://"))) {
            throw new FormException("The Helix ALM REST API address must start with http:// or https://.", formPath + API_ADDRESS);
        }

        if (StringUtils.isBlank(jObj.getString(CREDENTIALS_ID))) {
            throw new FormException("The Helix ALM connection credentials must be selected.", formPath + CREDENTIALS_ID);
        }
    }

    public static HALMGlobalConfig get() {
        return GlobalConfiguration.all()
            .get(HALMGlobalConfig.class);
    }

    public List<HALMConnection> getConnections() {
        return new ArrayList<>(this.connections);
    }

    public void setConnections(List<HALMConnection> connections) {
        this.connections = new ArrayList<>(connections);
    }

    /**
     * This looks up a connection by either its name or ID. Useful in the context of a pipeline where we could have
     * either. Names are not unique, but IDs are.
     *
     * @param nameOrID Name or ID to search for
     * @return Returns the first match it finds, without regard for the order of the list.
     */
    public HALMConnection getConnectionByNameOrID(String nameOrID) {
        return getConnections().stream()
            .filter(connection -> (nameOrID.equals(connection.getConnectionName()) ||
                    nameOrID.equals(connection.getConnectionUUID())))
            .findAny().orElse(null);
    }

    public List<? extends Descriptor> descriptors() {
        return Collections.singletonList(Jenkins.get().getDescriptor(HALMConnection.class));
    }
}

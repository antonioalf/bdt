/*
 * Copyright (C) 2014 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stratio.qa.specs;

import com.stratio.qa.utils.ThreadProperty;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.When;
import io.cucumber.datatable.DataTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generic ADFS Management Specs.
 *
 * @see <a href="ADFSSpec-annotations.html">ADFS Management Specs</a>
 */
public class ADFSSpec extends BaseGSpec {

    private final Logger logger = LoggerFactory.getLogger(ADFSSpec.class);

    GosecSpec gosecSpec;

    CCTSpec cctSpec;

    CommandExecutionSpec commandSpec;

    String adfsCN;

    /**
     * Generic constructor.
     *
     * @param spec object
     */
    public ADFSSpec(CommonG spec) {
        this.commonspec = spec;
        gosecSpec = new GosecSpec(spec);
        cctSpec = new CCTSpec(spec);
        commandSpec = new CommandExecutionSpec(spec);
        adfsCN = null;
    }

    /**
     * Create sis-synchronizer secrets
     */
    @Given("^I create sis-synchronizer secrets for CN '(.+?)'$")
    public void createSisSynchronizerSecrets(String adfsCN) throws Exception {
        // Set REST connection
        commonspec.setCCTConnection(null, null);
        gosecSpec.createUserResource(adfsCN, null, null, "keytab", "certificate", null);
        this.adfsCN = adfsCN;
    }

    /**
     * Create ADFS resource if it does not exist
     */
    @When("^I create federated '(user|group)' '(.+?)' based on '([^:]+?)'( as '(json|string|gov)')? with:$")
    public void createADFSResourceIfNotExist(String resource, String resourceId, String baseData, String type, DataTable modifications) throws Exception {

        // Initial checks
        assertThat(System.getProperty("DCOS_IP")).as("DCOS_IP needs to be provided!").isNotNull();
        assertThat(System.getProperty("REMOTE_USER")).as("REMOTE_USER needs to be provided!").isNotNull();
        assertThat(System.getProperty("PEM_FILE_PATH")).as("PEM_FILE_PATH needs to be provided!").isNotNull();

        // Obtain sis-synchronizer secrets
        assertThat(this.adfsCN).isNotNull();
        cctSpec.getCertificate(this.adfsCN, this.adfsCN, "inPeople");
        // Check certificate obtained correctly
        String command = "sudo openssl x509 -in target/test-classes/" + this.adfsCN + ".pem -noout -text";
        String expectedResult = "CN=" + this.adfsCN;
        commonspec.runLocalCommand(command);
        assertThat(commonspec.getCommandResult()).as("Contains " + expectedResult + ".").contains(expectedResult);

//        // Obtain CA
//        cctSpec.getCA();

        // Retrieve data
        String retrievedData = commonspec.retrieveData(baseData, type);
        // Modify data
        commonspec.getLogger().debug("Modifying data {} as {}", retrievedData, type);
        String modifiedData = commonspec.modifyData(retrievedData, type, modifications);
        modifiedData = modifiedData.replace("\"", "\\\"");

        // Create user
        String server = "https://gosec-identities-daas.marathon.mesos:8443";

        String endPoint = null;

        switch (resource) {
            case "user":
                endPoint = "/identities/users?generateKeytab=true&generateCertificate=true";
                break;
            case "group":
                endPoint = "/identities/groups";
                break;
            default:
                break;
        }

        String certPath = "/tmp/" + this.adfsCN + ".pem";
        String keyPath = "/tmp/" + this.adfsCN + ".key";
//        String caPath = "target/test-classes/ca.crt";
        String caPath = "/opt/mesosphere/etc/pki/ca-bundle.pem";
        String commandContent = " -H \"Content-Type: application/json\" --cacert \"" + caPath + "\" --cert \"" + certPath + "\" --key \"" + keyPath + "\" -d \"" + modifiedData + "\"";
        String commandUserCreation = "sudo curl -X POST \"" + server + endPoint + "\" " + commandContent + " -v";

        // Open connection to DCOS master
        commandSpec.openSSHConnection(ThreadProperty.get("DCOS_IP"), null, System.getProperty("REMOTE_USER"), null, System.getProperty("PEM_FILE_PATH"), null);

        // Copy secrets to DCOS master
        commonspec.getRemoteSSHConnection().copyTo("target/test-classes/" + this.adfsCN + ".pem", "/tmp/" + this.adfsCN + ".pem");
        commonspec.getRemoteSSHConnection().copyTo("target/test-classes/" + this.adfsCN + ".key", "/tmp/" + this.adfsCN + ".key");

        commonspec.runCommandAndGetResult(commandUserCreation);
        assertThat(commonspec.getCommandResult()).contains("HTTP/1.1 201");
    }

}

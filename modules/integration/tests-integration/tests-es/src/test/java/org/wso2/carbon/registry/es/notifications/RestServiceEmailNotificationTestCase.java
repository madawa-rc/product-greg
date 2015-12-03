/*
*Copyright (c) 2005-2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*WSO2 Inc. licenses this file to you under the Apache License,
*Version 2.0 (the "License"); you may not use this file except
*in compliance with the License.
*You may obtain a copy of the License at
*
*http://www.apache.org/licenses/LICENSE-2.0
*
*Unless required by applicable law or agreed to in writing,
*software distributed under the License is distributed on an
*"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*KIND, either express or implied.  See the License for the
*specific language governing permissions and limitations
*under the License.
*/

package org.wso2.carbon.registry.es.notifications;

import org.apache.wink.client.ClientResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.annotations.*;
import org.wso2.carbon.automation.engine.configurations.UrlGenerationUtil;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.automation.engine.frameworkutils.FrameworkPathUtil;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.es.utils.EmailUtil;
import org.wso2.carbon.registry.es.utils.GregESTestBaseTest;
import org.wso2.greg.integration.common.utils.GenericRestClient;

import javax.mail.MessagingException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * This test class can be used to check the email notification functionality of rest services
 * at the publisher.
 */
public class RestServiceEmailNotificationTestCase extends GregESTestBaseTest {

    private TestUserMode userMode;
    String jSessionId;
    private String publisherUrl;
    private String resourcePath;
    private String assetId;
    private String cookieHeader;
    private GenericRestClient genericRestClient;
    private Map<String, String> queryParamMap;
    private Map<String, String> headerMap;
    private String loginURL;
    boolean isNotificationMailAvailable;


    @Factory(dataProvider = "userModeProvider")
    public RestServiceEmailNotificationTestCase(TestUserMode userMode) {
        this.userMode = userMode;
    }

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {

        super.init(userMode);
        loginURL = UrlGenerationUtil.getLoginURL(automationContext.getInstance());
        genericRestClient = new GenericRestClient();
        queryParamMap = new HashMap<>();
        headerMap = new HashMap<>();
        resourcePath =
                FrameworkPathUtil.getSystemResourceLocation() + "artifacts" + File.separator + "GREG" + File.separator;
        publisherUrl = automationContext.getContextUrls().getSecureServiceUrl().replace("services", "publisher/apis");

        EmailUtil.updateProfileAndEnableEmailConfiguration(automationContext, backendURL, sessionCookie);
        setTestEnvironment();
    }

    /**
     * This test case add subscription to lifecycle state change and verifies the reception of email notification
     * by changing the life cycle state.
     */
    @Test(groups = { "wso2.greg",
            "wso2.greg.es" }, description = "Adding subscription to rest service on LC state change",
            dependsOnMethods = { "addSubscriptionCheckListItem" ,"addSubscriptionUnCheckListItem" })
    public void addSubscriptionToLcStateChange() throws Exception {

        JSONObject dataObject = new JSONObject();
        dataObject.put("notificationType", "PublisherLifeCycleStateChanged");
        dataObject.put("notificationMethod", "email");

        ClientResponse response = genericRestClient
                .geneticRestRequestPost(publisherUrl + "/subscriptions/restservice/" + assetId,
                        MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, dataObject.toString(), queryParamMap,
                        headerMap, cookieHeader);

        String payLoad = response.getEntity(String.class);
        payLoad = payLoad.substring(payLoad.indexOf('{'));
        JSONObject obj = new JSONObject(payLoad);
        assertNotNull(obj.get("id"),
                "Response payload is not the in the correct format" + response.getEntity(String.class));

        // verify e-mail
        String pointBrowserURL = EmailUtil.readGmailInboxForVerification();
        assertTrue(pointBrowserURL.contains("https"), "Verification mail has failed to reach Gmail inbox");
        EmailUtil.browserRedirectionOnVerification(pointBrowserURL, loginURL,
                automationContext.getContextTenant().getContextUser().getUserName(),
                automationContext.getContextTenant().getContextUser().getPassword());

        // Change the life cycle state in order to retrieve e-mail

        genericRestClient.geneticRestRequestPost(publisherUrl + "/assets/" + assetId + "/state",
                MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON,
                "nextState=Testing&comment=Completed", queryParamMap, headerMap, cookieHeader);

        isNotificationMailAvailable = EmailUtil.readGmailInboxForNotification("PublisherLifeCycleStateChanged");
        assertTrue(isNotificationMailAvailable,
                "Publisher lifecycle state changed notification mail has failed to reach Gmail inbox");
        isNotificationMailAvailable = false;
    }

    /**
     * This test case add subscription to resource update and verifies the reception of email notification
     * by updating the resource.
     */
    @Test(groups = { "wso2.greg",
            "wso2.greg.es" }, description = "Adding subscription to rest service on resource update")
    public void addSubscriptionOnResourceUpdate() throws Exception {

        JSONObject dataObject = new JSONObject();
        dataObject.put("notificationType", "PublisherResourceUpdated");
        dataObject.put("notificationMethod", "email");

        ClientResponse response = genericRestClient
                .geneticRestRequestPost(publisherUrl + "/subscriptions/restservice/" + assetId,
                        MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, dataObject.toString(), queryParamMap,
                        headerMap, cookieHeader);

        String payLoad = response.getEntity(String.class);
        payLoad = payLoad.substring(payLoad.indexOf('{'));
        JSONObject obj = new JSONObject(payLoad);
        assertNotNull(obj.get("id"),
                "Response payload is not the in the correct format" + response.getEntity(String.class));

        //verify e-mail
        String pointBrowserURL = EmailUtil.readGmailInboxForVerification();
        assertTrue(pointBrowserURL.contains("https"), "Verification mail has failed to reach Gmail inbox");
        EmailUtil.browserRedirectionOnVerification(pointBrowserURL, loginURL,
                automationContext.getContextTenant().getContextUser().getUserName(),
                automationContext.getContextTenant().getContextUser().getPassword());

        // update the resource in order to retrieve e-mail
        String dataBody = readFile(resourcePath + "json" + File.separator + "PublisherRestResourceUpdate.json");
        response = genericRestClient
                .geneticRestRequestPost(publisherUrl + "/assets/" + assetId, MediaType.APPLICATION_JSON,
                        MediaType.APPLICATION_JSON, dataBody, queryParamMap, headerMap, cookieHeader);
        obj = new JSONObject(response.getEntity(String.class));
        assertTrue((response.getStatusCode() == Response.Status.ACCEPTED.getStatusCode()),
                "Wrong status code ,Expected 202 Created ,Received " + response.getStatusCode());
        assertTrue(obj.getJSONObject("attributes").get("overview_context").equals("/changed/Context"));

        isNotificationMailAvailable = EmailUtil.readGmailInboxForNotification("PublisherResourceUpdated");
        assertTrue(isNotificationMailAvailable, "Publisher resource updated mail has failed to reach Gmail nbox");
        isNotificationMailAvailable = false;

    }

    /**
     * This test case add subscription to selecting check list item of life cycle and verifies
     * the reception of email notification by selecting the check list item.
     */
    @Test(groups = { "wso2.greg",
            "wso2.greg.es" }, description = "Adding subscription to rest service on check list item checked")
    public void addSubscriptionCheckListItem() throws Exception {

        JSONObject dataObject = new JSONObject();
        dataObject.put("notificationType", "PublisherCheckListItemChecked");
        dataObject.put("notificationMethod", "email");

        ClientResponse response = genericRestClient
                .geneticRestRequestPost(publisherUrl + "/subscriptions/restservice/" + assetId,
                        MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, dataObject.toString(), queryParamMap,
                        headerMap, cookieHeader);

        String payLoad = response.getEntity(String.class);
        payLoad = payLoad.substring(payLoad.indexOf('{'));
        JSONObject obj = new JSONObject(payLoad);
        assertNotNull(obj.get("id"),
                "Response payload is not the in the correct format" + response.getEntity(String.class));

        // verify e-mail
        String pointBrowserURL = EmailUtil.readGmailInboxForVerification();
        assertTrue(pointBrowserURL.contains("https"), "Verification mail has failed to reach Gmail inbox");
        EmailUtil.browserRedirectionOnVerification(pointBrowserURL, loginURL,
                automationContext.getContextTenant().getContextUser().getUserName(),
                automationContext.getContextTenant().getContextUser().getPassword());

        // check items on LC
        queryParamMap.put("lifecycle", "ServiceLifeCycle");
        JSONObject checkListObject = new JSONObject();
        JSONObject checkedItems = new JSONObject();
        JSONArray checkedItemsArray = new JSONArray();
        checkedItems.put("index", 0);
        checkedItems.put("checked", true);
        checkedItemsArray.put(checkedItems);
        checkListObject.put("checklist", checkedItemsArray);

        genericRestClient.geneticRestRequestPost(publisherUrl + "/asset/" + assetId + "/update-checklist",
                MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, checkListObject.toString(), queryParamMap,
                headerMap, cookieHeader);
        isNotificationMailAvailable = EmailUtil.readGmailInboxForNotification("PublisherCheckListItemChecked");
        assertTrue(isNotificationMailAvailable,
                "Publisher check list item ticked on life cycle, notification mail has failed to reach Gmail inbox");
        isNotificationMailAvailable = false;
    }

    /**
     * This test case add subscription to un ticking check list item of life cycle and verifies
     * the reception of email notification by un ticking the check list item.
     */
    @Test(groups = { "wso2.greg",
            "wso2.greg.es" }, description = "Adding subscription to rest service on check list item unchecked",
            dependsOnMethods = { "addSubscriptionCheckListItem" })
    public void addSubscriptionUnCheckListItem() throws Exception {

        JSONObject dataObject = new JSONObject();
        dataObject.put("notificationType", "PublisherCheckListItemUnchecked");
        dataObject.put("notificationMethod", "email");

        ClientResponse response = genericRestClient
                .geneticRestRequestPost(publisherUrl + "/subscriptions/restservice/" + assetId,
                        MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, dataObject.toString(), queryParamMap,
                        headerMap, cookieHeader);

        String payLoad = response.getEntity(String.class);
        payLoad = payLoad.substring(payLoad.indexOf('{'));
        JSONObject obj = new JSONObject(payLoad);
        assertNotNull(obj.get("id"),
                "Response payload is not the in the correct format" + response.getEntity(String.class));

        // verify e-mail
        String pointBrowserURL = EmailUtil.readGmailInboxForVerification();
        assertTrue(pointBrowserURL.contains("https"), "Verification mail has failed to reach Gmail inbox");
        EmailUtil.browserRedirectionOnVerification(pointBrowserURL, loginURL,
                automationContext.getContextTenant().getContextUser().getUserName(),
                automationContext.getContextTenant().getContextUser().getPassword());

        // un check items on LC
        JSONObject checkListObject = new JSONObject();
        JSONObject checkedItems = new JSONObject();
        JSONArray checkedItemsArray = new JSONArray();
        checkedItems.put("index", 0);
        checkedItems.put("checked", false);
        checkedItemsArray.put(checkedItems);
        checkListObject.put("checklist", checkedItemsArray);

        genericRestClient.geneticRestRequestPost(publisherUrl + "/asset/" + assetId + "/update-checklist",
                MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, checkListObject.toString(), queryParamMap,
                headerMap, cookieHeader);
        isNotificationMailAvailable = EmailUtil.readGmailInboxForNotification("PublisherCheckListItemUnchecked");
        assertTrue(isNotificationMailAvailable,
                "Publisher uncheck list item on life cycle, notification mail has failed to reach Gmail inbox");
        isNotificationMailAvailable = false;

    }

    /**
     * Method used to authenticate publisher and create a rest service asset. Created asset
     * is used to add subscriptions and to receive notification.
     */
    private void setTestEnvironment() throws JSONException, IOException, XPathExpressionException {
        // Authenticate Publisher
        ClientResponse response = authenticate(publisherUrl, genericRestClient,
                automationContext.getSuperTenant().getTenantAdmin().getUserName(),
                automationContext.getSuperTenant().getTenantAdmin().getPassword());
        JSONObject obj = new JSONObject(response.getEntity(String.class));
        jSessionId = obj.getJSONObject("data").getString("sessionId");
        cookieHeader = "JSESSIONID=" + jSessionId;

        //Create rest service
        queryParamMap.put("type", "restservice");
        String dataBody = readFile(resourcePath + "json" + File.separator + "publisherPublishRestResource.json");
        ClientResponse createResponse = genericRestClient
                .geneticRestRequestPost(publisherUrl + "/assets", MediaType.APPLICATION_JSON,
                        MediaType.APPLICATION_JSON, dataBody, queryParamMap, headerMap, cookieHeader);
        JSONObject createObj = new JSONObject(createResponse.getEntity(String.class));
        assetId = (String) createObj.get("id");
    }

    @AfterClass(alwaysRun = true)
    public void cleanUp() throws RegistryException, JSONException, IOException, MessagingException {
        deleteAssetById(publisherUrl, genericRestClient, cookieHeader, assetId, queryParamMap);
        EmailUtil.deleteSentMails();
    }

    @DataProvider
    private static TestUserMode[][] userModeProvider() {
        return new TestUserMode[][] { new TestUserMode[] { TestUserMode.SUPER_TENANT_ADMIN }
                //                new TestUserMode[]{TestUserMode.TENANT_USER},
        };
    }
}

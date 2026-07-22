package builders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import core.Configs;
import core.PropertiesHolder;
import io.gatling.javaapi.http.HttpRequestActionBuilder;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class Requests {

    /**
     * JSON-RPC "tools/list" request body, shared by the application and toolset MCP endpoints.
     */
    private static final String TOOLS_LIST_PAYLOAD = """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "method": "tools/list",
              "params": {"cursor": "optional-cursor-value"}
            }""";

    public static HttpRequestActionBuilder createModelAPI(String modelName) {
        String payload = """
                {
                  "name": "%s",
                  "description": "",
                  "endpoint": "%s",
                  "displayVersion": "",
                  "displayName": "%s",
                  "type": "chat"
                }""".formatted(modelName, PropertiesHolder.modelEndpoint, modelName);

        return http("Create Model API")
                .post("/api/v1/models")
                .headers(Configs.DIAL_ADMIN_API_HEADERS)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder getAllModelsAPI() {
        return http("Get all Models API")
                .get("/api/v1/models")
                .headers(Configs.DIAL_ADMIN_API_HEADERS);
    }

    public static HttpRequestActionBuilder getModelDetailsAPI(String modelName) {
        return http("Get Model Details")
                .get("/api/v1/models/" + modelName)
                .headers(Configs.DIAL_ADMIN_API_HEADERS);
    }

    public static HttpRequestActionBuilder syncModelStateAPI(String modelName) {
        return http("Sync Model State")
                .get("/api/v1/models/" + modelName + "/sync-state")
                .headers(Configs.DIAL_ADMIN_API_HEADERS)
                .check(bodyString().saveAs("syncModelStateAPIResponseBody"))
                .check(jsonPath("$.status").saveAs("syncModelStatus"));
    }

    /*
    ***************************************************************
    * Application requests
    ***************************************************************
    */

    public static HttpRequestActionBuilder mcpToolsList(String bucket, String appPath) {
        return http("MCP tools/list")
                .post("/v1/deployments/applications/" + bucket + "/" + appPath + "/mcp")
                .headers(Configs.DIAL_CORE_API_HEADERS)
                .body(StringBody(TOOLS_LIST_PAYLOAD));
    }

    public static HttpRequestActionBuilder getApplication(String bucket, String appPath) {
        return http("Get Application")
                .get("/v1/applications/" + bucket + "/" + appPath)
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    public static HttpRequestActionBuilder getApplicationTypeSchemas() {
        return http("Get Application Type Schemas")
                .get("/v1/application_type_schemas/schemas")
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    public static HttpRequestActionBuilder getApplicationTypeSchema(String schemaId) {
        return http("Get Application Type Schema")
                .get("/v1/application_type_schemas/schema")
                .queryParam("id", schemaId)
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    public static HttpRequestActionBuilder updateApplicationMcp(String bucket, String appPath) {
        return http("Update Application (MCP)")
                .put("/v1/applications/" + bucket + "/" + appPath + "/mcp")
                .headers(Configs.DIAL_CORE_API_HEADERS)
                .body(StringBody(""));
    }

    public static HttpRequestActionBuilder getApplicationMetadata(String bucket, String appPath) {
        return http("Get Application Metadata")
                .get("/v1/metadata/applications/" + bucket + "/" + appPath)
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    /*
    ***************************************************************
    * Toolset requests
    ***************************************************************
    */

    public static HttpRequestActionBuilder getBucket() {
        return http("Get Bucket")
                .get("/v1/bucket")
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    public static HttpRequestActionBuilder updateToolset(String bucket, String toolsetName, String name) {
        String payload = """
                {
                  "path": "%s/%s",
                  "version": "1.0.0",
                  "folderId": "%s/",
                  "updatedAt": 1783512703271,
                  "author": "dial_admin@gke.test.epam-rail.com",
                  "name": "%s",
                  "endpoint": "%s",
                  "displayName": "%s",
                  "displayVersion": "1.0.0",
                  "description": "",
                  "descriptionKeywords": [],
                  "maxRetryAttempts": 1,
                  "createdAt": 1783430411237,
                  "transport": "http",
                  "allowedTools": [],
                  "authSettings": {"authenticationType": "none", "globalAuthStatus": "signed_out", "userLevelAuthStatus": "signed_out"},
                  "forwardPerRequestKey": false,
                  "forwardAuthToken": false
                }""".formatted(bucket, toolsetName, bucket, name, PropertiesHolder.toolsetEndpoint, name);

        return http("Update Toolset")
                .put("/v1/toolsets/" + bucket + "/" + name)
                .headers(Configs.DIAL_CORE_API_HEADERS)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder toolsetMcpToolsList(String bucket, String toolsetName) {
        return http("Toolset MCP tools/list")
                .post("/v1/toolset/toolsets/" + bucket + "/" + toolsetName + "/mcp")
                .headers(Configs.DIAL_CORE_API_HEADERS)
                .body(StringBody(TOOLS_LIST_PAYLOAD));
    }

    public static HttpRequestActionBuilder getToolset(String bucket, String toolsetName) {
        return http("Get Toolset")
                .get("/v1/toolsets/" + bucket + "/" + toolsetName)
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    public static HttpRequestActionBuilder getToolsetTools(String bucket, String toolsetName) {
        return http("Get Toolset Tools")
                .get("/v1/toolset/toolsets/" + bucket + "/" + toolsetName + "/tools")
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    public static HttpRequestActionBuilder getToolsetAllowedTools(String bucket, String toolsetName) {
        return http("Get Toolset Allowed Tools")
                .get("/v1/toolset/toolsets/" + bucket + "/" + toolsetName + "/allowed-tools")
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    public static HttpRequestActionBuilder getToolsetMetadata(String bucket, String toolsetName) {
        return http("Get Toolset Metadata")
                .get("/v1/metadata/toolsets/" + bucket + "/" + toolsetName)
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    public static HttpRequestActionBuilder deleteToolset(String bucket, String toolsetName) {
        return http("Delete Toolset")
                .delete("/v1/toolsets/" + bucket + "/" + toolsetName)
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    /*
    ***************************************************************
    * Prompt requests
    ***************************************************************
    */

    public static HttpRequestActionBuilder updatePrompt(String bucket, String promptName, String displayName) {
        String payload = """
                {
                  "id": "prompts/%s/%s",
                  "name": "%s",
                  "description": "XC3Dabf",
                  "content": "biYcV5p",
                  "folderId": "prompts/%s"
                }""".formatted(bucket, promptName, displayName, bucket);

        return http("Update Prompt")
                .put("/v1/prompts/" + bucket + "/" + promptName)
                .headers(Configs.DIAL_CORE_API_HEADERS)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder getPrompt(String bucket, String promptName) {
        return http("Get Prompt")
                .get("/v1/prompts/" + bucket + "/" + promptName)
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    public static HttpRequestActionBuilder getPromptMetadata(String bucket, String promptName) {
        return http("Get Prompt Metadata")
                .get("/v1/metadata/prompts/" + bucket + "/" + promptName)
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    public static HttpRequestActionBuilder deletePrompt(String bucket, String promptName) {
        return http("Delete Prompt")
                .delete("/v1/prompts/" + bucket + "/" + promptName)
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    /*
    ***************************************************************
    * Files requests
    ***************************************************************
    */

    public static HttpRequestActionBuilder getFile(String bucket, String filePath) {
        return http("Get File")
                .get("/v1/files/" + bucket + "/" + filePath)
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    public static HttpRequestActionBuilder getFileMetadata(String bucket, String filePath) {
        return http("Get File Metadata")
                .get("/v1/metadata/files/" + bucket + "/" + filePath)
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    public static HttpRequestActionBuilder copyResource(String sourceUrl, String destinationUrl) {
        String payload = """
                {
                  "sourceUrl": "%s",
                  "destinationUrl": "%s",
                  "overwrite": true
                }""".formatted(sourceUrl, destinationUrl);

        return http("Copy Resource")
                .post("/v1/ops/resource/copy")
                .headers(Configs.DIAL_CORE_API_HEADERS)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder moveResource(String sourceUrl, String destinationUrl) {
        String payload = """
                {
                  "sourceUrl": "%s",
                  "destinationUrl": "%s",
                  "overwrite": true
                }""".formatted(sourceUrl, destinationUrl);

        return http("Move Resource")
                .post("/v1/ops/resource/move")
                .headers(Configs.DIAL_CORE_API_HEADERS)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder deleteFile(String bucket, String filePath) {
        return http("Delete File")
                .delete("/v1/files/" + bucket + "/" + filePath)
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    /*
    ***************************************************************
    * Deployment listing requests (DIAL Core, Api-Key authenticated)
    * Ported from the "Deployment Listing" Postman collection.
    ***************************************************************
    */

    public static HttpRequestActionBuilder listDeployments() {
        return http("List Deployments")
                .get("/v1/deployments")
                .queryParam("interface_type", "all")
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    public static HttpRequestActionBuilder listOpenAiDeployments() {
        return http("List OpenAI Deployments")
                .get("/openai/deployments")
                .headers(Configs.DIAL_CORE_API_HEADERS)
                .check(jsonPath("$.data[*].id").findRandom().saveAs("randomDeploymentId"));
    }

    public static HttpRequestActionBuilder getOpenAiDeployment() {
        return http("Get OpenAI Deployment")
                .get("/openai/deployments/#{randomDeploymentId}")
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    public static HttpRequestActionBuilder listOpenAiModels() {
        return http("List OpenAI Models")
                .get("/openai/models")
                .headers(Configs.DIAL_CORE_API_HEADERS)
                .check(jsonPath("$.data[*].id").findRandom().saveAs("randomModelId"));
    }

    public static HttpRequestActionBuilder getOpenAiModel() {
        return http("Get OpenAI Model")
                .get("/openai/models/#{randomModelId}")
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    public static HttpRequestActionBuilder listOpenAiApplications() {
        return http("List OpenAI Applications")
                .get("/openai/applications")
                .headers(Configs.DIAL_CORE_API_HEADERS)
                .check(jsonPath("$.data[*].id").findRandom().saveAs("randomApplicationId"));
    }

    public static HttpRequestActionBuilder getOpenAiApplication() {
        return http("Get OpenAI Application")
                .get("/openai/applications/#{randomApplicationId}")
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    public static HttpRequestActionBuilder listOpenAiToolsets() {
        return http("List OpenAI Toolsets")
                .get("/openai/toolsets")
                .headers(Configs.DIAL_CORE_API_HEADERS)
                .check(jsonPath("$.data[*].id").findRandom().saveAs("randomToolsetId"));
    }

    public static HttpRequestActionBuilder getOpenAiToolset() {
        return http("Get OpenAI Toolset")
                .get("/openai/toolsets/#{randomToolsetId}")
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    /*
    ***************************************************************
    * Sharing requests (DIAL Core "Sharing" API tag)
    * The header map is passed in so the same builder serves both the resource
    * owner (Api-Key #1) and the invitation receiver (Api-Key #2).
    ***************************************************************
    */

    public static HttpRequestActionBuilder getBucket(Map<String, String> headers, String saveBucketAs) {
        return http("Share - Get Bucket")
                .get("/v1/bucket")
                .headers(headers)
                .check(jsonPath("$.bucket").saveAs(saveBucketAs));
    }

    public static HttpRequestActionBuilder createSharePrompt(Map<String, String> headers, String bucket, String promptName) {
        String payload = """
                {
                  "id": "prompts/%s/%s",
                  "name": "%s",
                  "description": "perf share resource",
                  "content": "perf share content",
                  "folderId": "prompts/%s"
                }""".formatted(bucket, promptName, promptName, bucket);

        return http("Share - Create Prompt Resource")
                .put("/v1/prompts/" + bucket + "/" + promptName)
                .headers(headers)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder shareResource(String requestName, Map<String, String> headers,
                                                         String resourceUrl, String permission, String saveInvitationLinkAs) {
        String payload = """
                {
                  "invitationType": "link",
                  "resources": [
                    {
                      "url": "%s",
                      "permissions": ["%s"]
                    }
                  ]
                }""".formatted(resourceUrl, permission);

        return http(requestName)
                .post("/v1/ops/resource/share/create")
                .headers(headers)
                .body(StringBody(payload))
                .check(jsonPath("$.invitationLink").saveAs(saveInvitationLinkAs));
    }

    public static HttpRequestActionBuilder getSharedResources(String requestName, Map<String, String> headers,
                                                              String resourceTypesJson, String with) {
        String payload = """
                {
                  "resourceTypes": [%s],
                  "with": "%s"
                }""".formatted(resourceTypesJson, with);

        return http(requestName)
                .post("/v1/ops/resource/share/list")
                .headers(headers)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder revokeSharedResources(Map<String, String> headers, String resourceUrl) {
        String payload = """
                {
                  "resources": [
                    {
                      "url": "%s"
                    }
                  ]
                }""".formatted(resourceUrl);

        return http("Share - Revoke Access")
                .post("/v1/ops/resource/share/revoke")
                .headers(headers)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder discardSharedResources(Map<String, String> headers, String resourceUrl) {
        String payload = """
                {
                  "resources": [
                    {
                      "url": "%s"
                    }
                  ]
                }""".formatted(resourceUrl);

        return http("Share - Discard Access")
                .post("/v1/ops/resource/share/discard")
                .headers(headers)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder copySharedResources(Map<String, String> headers, String sourceUrl, String destinationUrl) {
        String payload = """
                {
                  "sourceUrl": "%s",
                  "destinationUrl": "%s"
                }""".formatted(sourceUrl, destinationUrl);

        return http("Share - Copy Access")
                .post("/v1/ops/resource/share/copy")
                .headers(headers)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder getInvitations(Map<String, String> headers) {
        return http("Share - List Invitations")
                .get("/v1/invitations")
                .headers(headers);
    }

    public static HttpRequestActionBuilder getInvitation(Map<String, String> headers, String invitationLinkEl) {
        return http("Share - Get Invitation")
                .get("/" + invitationLinkEl)
                .headers(headers);
    }

    public static HttpRequestActionBuilder acceptInvitation(Map<String, String> headers, String invitationLinkEl) {
        return http("Share - Accept Invitation")
                .get("/" + invitationLinkEl)
                .queryParam("accept", "true")
                .headers(headers);
    }

    public static HttpRequestActionBuilder deleteInvitation(Map<String, String> headers, String invitationLinkEl) {
        return http("Share - Delete Invitation")
                .delete("/" + invitationLinkEl)
                .headers(headers);
    }

    public static HttpRequestActionBuilder grantPerRequestPermissions(Map<String, String> headers, String resourceUrl,
                                                                      String permission, String receiver) {
        String payload = """
                {
                  "resources": [
                    {
                      "url": "%s",
                      "permissions": ["%s"]
                    }
                  ],
                  "receiver": "%s"
                }""".formatted(resourceUrl, permission, receiver);

        return http("Per-Request Permissions - Grant")
                .post("/v1/ops/resource/per-request-permissions/grant")
                .headers(headers)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder revokePerRequestPermissions(Map<String, String> headers, String resourceUrl,
                                                                       String permission, String receiver) {
        String payload = """
                {
                  "resources": [
                    {
                      "url": "%s",
                      "permissions": ["%s"]
                    }
                  ],
                  "receiver": "%s"
                }""".formatted(resourceUrl, permission, receiver);

        return http("Per-Request Permissions - Revoke")
                .post("/v1/ops/resource/per-request-permissions/revoke")
                .headers(headers)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder getPerRequestPermissions(Map<String, String> headers, String with) {
        String payload = """
                {
                  "with": "%s"
                }""".formatted(with);

        return http("Per-Request Permissions - List")
                .post("/v1/ops/resource/per-request-permissions/list")
                .headers(headers)
                .body(StringBody(payload));
    }

    /*
    ***************************************************************
    * Publication requests (DIAL Core "Publications" API tag)
    ***************************************************************
    */

    // Conversation resource used as the publication subject (mirrors PublicationApiTest).
    public static HttpRequestActionBuilder createConversation(Map<String, String> headers, String bucket, String folder, String name) {
        String payload = """
                {
                  "id": "conversations/%s/%s/%s",
                  "name": "%s",
                  "messages": [],
                  "model": {"id": "gpt-4"},
                  "prompt": "",
                  "temperature": 1,
                  "folderId": "conversations/%s/%s",
                  "replay": {"isReplay": false, "replayUserMessagesStack": [], "activeReplayIndex": 0},
                  "selectedAddons": [],
                  "lastActivityDate": 0
                }""".formatted(bucket, folder, name, name, bucket, folder);

        return http("Publication - Create Conversation")
                .put("/v1/conversations/" + bucket + "/" + folder + "/" + name)
                .headers(headers)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder deleteConversation(Map<String, String> headers, String bucket, String folder, String name) {
        return http("Publication - Delete Conversation")
                .delete("/v1/conversations/" + bucket + "/" + folder + "/" + name)
                .headers(headers);
    }

    public static HttpRequestActionBuilder getPublications(Map<String, String> headers, String url) {
        String payload = """
                {
                  "url": "%s"
                }""".formatted(url);

        return http("Publication - List")
                .post("/v1/ops/publication/list")
                .headers(headers)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder getPublication(Map<String, String> headers, String url) {
        String payload = """
                {
                  "url": "%s"
                }""".formatted(url);

        return http("Publication - Get")
                .post("/v1/ops/publication/get")
                .headers(headers)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder createPublication(String requestName, Map<String, String> headers, String name,
                                                             String targetFolder, String sourceUrl, String targetUrl,
                                                             String savePublicationUrlAs) {
        String payload = """
                {
                  "name": "%s",
                  "targetFolder": "%s",
                  "resources": [
                    {
                      "action": "ADD",
                      "sourceUrl": "%s",
                      "targetUrl": "%s"
                    }
                  ],
                  "rules": [
                    {
                      "source": "roles",
                      "function": "EQUAL",
                      "targets": ["user"]
                    }
                  ]
                }""".formatted(name, targetFolder, sourceUrl, targetUrl);

        return http(requestName)
                .post("/v1/ops/publication/create")
                .headers(headers)
                .body(StringBody(payload))
                .check(jsonPath("$.url").saveAs(savePublicationUrlAs));
    }

    public static HttpRequestActionBuilder updatePublication(Map<String, String> headers, String url,
                                                             String targetFolder, String sourceUrl, String targetUrl) {
        String payload = """
                {
                  "url": "%s",
                  "targetFolder": "%s",
                  "resources": [
                    {
                      "action": "ADD",
                      "sourceUrl": "%s",
                      "targetUrl": "%s"
                    }
                  ],
                  "rules": [
                    {
                      "source": "roles",
                      "function": "EQUAL",
                      "targets": ["user"]
                    }
                  ]
                }""".formatted(url, targetFolder, sourceUrl, targetUrl);

        return http("Publication - Update")
                .post("/v1/ops/publication/update")
                .headers(headers)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder deletePublication(Map<String, String> headers, String url) {
        String payload = """
                {
                  "url": "%s"
                }""".formatted(url);

        return http("Publication - Delete")
                .post("/v1/ops/publication/delete")
                .headers(headers)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder rejectPublication(Map<String, String> headers, String url, String comment) {
        String payload = """
                {
                  "url": "%s",
                  "comment": "%s"
                }""".formatted(url, comment);

        return http("Publication - Reject")
                .post("/v1/ops/publication/reject")
                .headers(headers)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder approvePublication(Map<String, String> headers, String url) {
        String payload = """
                {
                  "url": "%s"
                }""".formatted(url);

        return http("Publication - Approve")
                .post("/v1/ops/publication/approve")
                .headers(headers)
                .body(StringBody(payload));
    }

    // The rule/list url is a *public folder* path (e.g. "public/" or "public/folder/"), not a publications url.
    public static HttpRequestActionBuilder getPublicationRules(Map<String, String> headers, String url) {
        String payload = """
                {
                  "url": "%s"
                }""".formatted(url);

        return http("Publication - List Rules")
                .post("/v1/ops/publication/rule/list")
                .headers(headers)
                .body(StringBody(payload));
    }

    /*
    ***************************************************************
    * TEMPORARY UNUSED METHODS FOR AI Dial Admin UI model creation
    ***************************************************************
    */

    private static final String MODEL_DETAILS_ROUTER_STATE_TREE = encodeRouterStateTree(
        "[\"\",{\"children\":[[\"lang\",\"en\",\"d\"],{\"children\":[\"models\",{\"children\":[[\"id\",\"perf_model_1770808223983\",\"d\"],{\"children\":[\"__PAGE__\",{},null,null]},null,null]},null,null]},null,null]},null,null,true]"
    );

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static String encodeRouterStateTree(String value) {
        return URLEncoder.encode(value, Charset.forName("UTF-8"));
    }

    public static HttpRequestActionBuilder getModelsPage() {
        return http("Get Models Page")
                .get("/en/models")
                .headers(Configs.ACCEPT_TEXT_HEADERS);
    }

    public static HttpRequestActionBuilder callCreateModelPopUp() {
        return http("Call Create Model PopUp")
                .post("/en/models")
                .body(StringBody("[]"))
                .headers(Configs.ACCEPT_TEXT_HEADERS)
                .check(bodyString().transform(Requests::extractRandomAdapterName).saveAs("adapterName"));
    }

    public static HttpRequestActionBuilder searchModel(String modelName) {
        return http("Search Model")
                .post("/en/models")
                .headers(Configs.ACCEPT_TEXT_HEADERS)
                .header("next-action", "4037f32bbbe005c3db5f4302397ee98f3e6dfc5259")
                .body(StringBody("[\"" + modelName + "\"]"));
    }

    public static HttpRequestActionBuilder createModel(String modelName) {
        String payload = "[{\"name\":\"" + modelName + "\"}]";

        return http("Create Model")
                .post("/en/models")
                .headers(Configs.ACCEPT_TEXT_HEADERS)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder createModelWithDetails(String modelName, String adapterName) {
        String payload = "[{\"name\":\"" + modelName + "\"," +
                "\"description\":\"\"," +
                "\"source\":{\"$type\":\"adapter\"," +
                "\"completionEndpointPath\":\"/chat/completions\"," +
                "\"adapterName\":\"" + adapterName + "\"}," +
                "\"displayName\":\"" + modelName + "\"," +
                "\"endpoint\":\"\"}]";

        return http("Create Model")
                .post("/en/models")
                .headers(Configs.ACCEPT_TEXT_HEADERS)
                .header("next-action", "40d13d7cea9f9cc3af9fb07d3c596756e5f2f304a4")
                .header("next-router-state-tree", MODEL_DETAILS_ROUTER_STATE_TREE)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder getModelDetails(String modelName) {
        return http("Get Model Details")
                .get("/en/models/" + modelName)
                .headers(Configs.ACCEPT_TEXT_HEADERS);
    }

    public static HttpRequestActionBuilder syncModelState(String modelName) {
        return http("Sync Model State")
                .post("/en/models/" + modelName)
                .headers(Configs.ACCEPT_TEXT_HEADERS)
                .header("next-action", "40332f07c8f7d374d91e6de636c8104170816e8085")
                .header("next-router-state-tree", "%5B%22%22%2C%7B%22children%22%3A%5B%5B%22lang%22%2C%22en%22%2C%22d%22%5D%2C%7B%22children%22%3A%5B%22models%22%2C%7B%22children%22%3A%5B%5B%22id%22%2C%22perf_model_1770808223983%22%2C%22d%22%5D%2C%7B%22children%22%3A%5B%22__PAGE__%22%2C%7B%7D%2Cnull%2Cnull%5D%7D%2Cnull%2Cnull%5D%7D%2Cnull%2Cnull%5D%7D%2Cnull%2Cnull%5D%7D%2Cnull%2Cnull%2Ctrue%5D")
                .body(StringBody("[\"/models/" + modelName + "/sync-state\",\"*\"]"))
                .check(bodyString().saveAs("syncModelStateResponseBody"));
    }

    private static String extractRandomAdapterName(String responseBody) {
        String[] lines = responseBody.split("\n");
        String jsonLine = null;
        for (String line : lines) {
            if (line.startsWith("1:")) {
                jsonLine = line.substring(2);
                break;
            }
        }

        if (jsonLine == null) {
            return "DIAL";
        }

        try {
            JsonNode responseArray = objectMapper.readTree(jsonLine).path("response");

            if (!responseArray.isArray() || responseArray.isEmpty()) {
                return "DIAL";
            }

            List<String> displayNames = new ArrayList<>();
            for (JsonNode adapter : responseArray) {
                String displayName = adapter.path("displayName").asText(null);
                if (displayName != null) {
                    displayNames.add(displayName);
                }
            }

            if (displayNames.isEmpty()) {
                return "DIAL";
            }

            return displayNames.get(new Random().nextInt(displayNames.size()));
        } catch (Exception e) {
            return "DIAL";
        }
    }
}

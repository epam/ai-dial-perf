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
    * AI DIAL Admin roles and keys
    ***************************************************************
    */

    public static HttpRequestActionBuilder createRoleAPI() {
        return http("Create Role")
                .post("/api/v1/roles")
                .headers(Configs.DIAL_CORE_CREATE_KEY_WITH_ROLE_HEADERS)
                .body(StringBody("""
                        {"name":"#{roleName}","displayName":"#{roleName}","description":""}
                        """));
    }

    public static HttpRequestActionBuilder createKeyWithRoleAPI() {
        return http("Create Key With Role")
                .post("/api/v1/keys")
                .headers(Configs.DIAL_CORE_CREATE_KEY_WITH_ROLE_HEADERS)
                .body(StringBody("""
                        {"name":"#{keyName}","key":"#{keyValue}","displayName":"#{displayName}","project":"#{project}","secured":true,"roles":["#{roleName}"],"description":"string","projectContactPoint":"string","expiresAt":null,"validityState":{"message":"string","valid":true},"topics":["string"],"allowedIpAddressRanges":null}
                        """));
    }

    public static HttpRequestActionBuilder getKeyAPI(String keyName) {
        return http("Get Key And Verify Assigned Role")
                .get("/api/v1/keys/" + keyName)
                .headers(Configs.DIAL_CORE_CREATE_KEY_WITH_ROLE_HEADERS);
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
                .headers(Configs.DIAL_CORE_API_HEADERS)
                .check(jsonPath("$.endpoint").saveAs("applicationEndpoint"))
                .check(jsonPath("$.display_name").saveAs("applicationDisplayName"))
                .check(jsonPath("$.display_version").saveAs("applicationDisplayVersion"))
                .check(jsonPath("$.mcp").saveAs("applicationMcpConfig"));
    }

    public static HttpRequestActionBuilder getApplicationTypeSchemas() {
        return http("Get Application Type Schemas")
                .get("/v1/application_type_schemas/schemas")
                .headers(Configs.DIAL_CORE_API_HEADERS)
                .check(jsonPath("$[*]['$id']").findRandom().saveAs("applicationSchemaId"));
    }

    public static HttpRequestActionBuilder getApplicationTypeSchema(String schemaId) {
        return http("Get Application Type Schema")
                .get("/v1/application_type_schemas/schema")
                .queryParam("id", schemaId)
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    public static HttpRequestActionBuilder updateApplicationMcp(String bucket, String appPath) {
        return http("Update Application (MCP)")
                .put("/v1/applications/" + bucket + "/" + appPath)
                .headers(Configs.DIAL_CORE_API_HEADERS)
                .body(StringBody("""
                        {
                          "endpoint": "#{applicationEndpoint}",
                          "display_name": "#{applicationDisplayName}",
                          "display_version": "#{applicationDisplayVersion}",
                          "mcp": #{applicationMcpConfig}
                        }"""));
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

    public static HttpRequestActionBuilder updateToolset(String bucket, String toolsetPath,
                                                         String toolsetName, String toolsetVersion) {
        String payload = """
                {
                  "endpoint": "#{toolsetEndpoint}",
                  "display_name": "%s",
                  "display_version": "%s",
                  "transport": "HTTP",
                  "allowedTools": [],
                  "authSettings": {"authenticationType": "NONE"}
                }""".formatted(toolsetName, toolsetVersion);

        return http("Update Toolset")
                .put("/v1/toolsets/" + bucket + "/" + toolsetPath)
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
                .headers(Configs.DIAL_CORE_API_IF_MATCH_ANY_HEADERS)
                .check(status().saveAs("toolsetDeleteStatus"));
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
        return deletePrompt(Configs.DIAL_CORE_API_HEADERS, bucket, promptName);
    }

    public static HttpRequestActionBuilder deletePrompt(Map<String, String> headers, String bucket, String promptName) {
        return http("Delete Prompt")
                .delete("/v1/prompts/" + bucket + "/" + promptName)
                .headers(headers)
                .check(status().is(200));
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
                .check(status().is(200))
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
                .body(StringBody(payload))
                .check(status().is(200));
    }

    public static HttpRequestActionBuilder shareResource(String requestName, Map<String, String> headers,
                                                         String resourceUrl, String permission, String saveInvitationLinkAs) {
        return shareResource(requestName, headers, resourceUrl, "\"" + permission + "\"", null,
                saveInvitationLinkAs);
    }

    public static HttpRequestActionBuilder shareResource(String requestName, Map<String, String> headers,
                                                         String resourceUrl, String permissionsJson,
                                                         Integer maxAcceptedUsers, String saveInvitationLinkAs) {
        String maxAcceptedUsersJson = maxAcceptedUsers == null
                ? ""
                : ",\n  \"maxAcceptedUsers\": " + maxAcceptedUsers;
        String payload = """
                {
                  "invitationType": "link",
                  "resources": [
                    {
                      "url": "%s",
                      "permissions": [%s]
                    }
                  ]%s
                }""".formatted(resourceUrl, permissionsJson, maxAcceptedUsersJson);

        return http(requestName)
                .post("/v1/ops/resource/share/create")
                .headers(headers)
                .body(StringBody(payload))
                .check(status().is(200))
                .check(jsonPath("$.invitationLink").saveAs(saveInvitationLinkAs));
    }

    public static HttpRequestActionBuilder getSharedResources(String requestName, Map<String, String> headers,
                                                              String resourceTypesJson, String with) {
        String payload = """
                {
                  "resourceTypes": [%s],
                  "with": "%s",
                  "includeUserInfo": true
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
                .body(StringBody(payload))
                .check(status().is(200));
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
                .get(invitationLinkEl)
                .headers(headers);
    }

//     public static HttpRequestActionBuilder acceptInvitation(Map<String, String> headers, String invitationLinkEl) {
//         return http("Share - Accept Invitation")
//                 .get(invitationLinkEl)
//                 .queryParam("accept", "true")
//                 .headers(headers);
//     }

    public static HttpRequestActionBuilder deleteInvitation(Map<String, String> headers, String invitationLinkEl) {
        return http("Share - Delete Invitation")
                .delete(invitationLinkEl)
                .headers(headers);
            }

    public static HttpRequestActionBuilder grantPerRequestPermissions(Map<String, String> headers, String resourceUrl,
                                                                      String permissionsJson, String receiver) {
        String payload = """
                {
                  "resources": [
                    {
                      "url": "%s",
                      "permissions": [%s]
                    }
                  ],
                  "receiver": "%s"
                }""".formatted(resourceUrl, permissionsJson, receiver);

        return http("Per-Request Permissions - Grant")
                .post("/v1/ops/resource/per-request-permissions/grant")
                .headers(headers)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder revokePerRequestPermissions(Map<String, String> headers, String resourceUrl,
                                                                       String permissionsJson, String receiver) {
        String payload = """
                {
                  "resources": [
                    {
                      "url": "%s",
                      "permissions": [%s]
                    }
                  ],
                  "receiver": "%s"
                }""".formatted(resourceUrl, permissionsJson, receiver);

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

        return http("Per-Request Permissions - List (" + with + ")")
                .post("/v1/ops/resource/per-request-permissions/list")
                .headers(headers)
                .body(StringBody(payload));
    }

    /*
    ***************************************************************
    * Publication requests (DIAL Core "Publications" API tag)
    *
    * These use absolute Core URLs because the scenario first authenticates against
    * the Admin UI host to obtain an administrator bearer token.
    ***************************************************************
    */

    private static String dialCoreUrl(String path) {
        String baseUrl = PropertiesHolder.dialCoreBaseUrl.replaceAll("/+$", "");
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    public static HttpRequestActionBuilder getPublicationBucket(String saveBucketAs) {
        return http("Publication - Get Owner Bucket")
                .get(dialCoreUrl("/v1/bucket"))
                .headers(Configs.DIAL_CORE_API_HEADERS)
                .check(jsonPath("$.bucket").saveAs(saveBucketAs));
    }

    public static HttpRequestActionBuilder createPublicationPrompt(String bucket, String promptName) {
        String payload = """
                {
                  "id": "prompts/%s/%s",
                  "name": "%s",
                  "description": "publication performance resource",
                  "content": "publication performance content",
                  "folderId": "prompts/%s"
                }""".formatted(bucket, promptName, promptName, bucket);

        return http("Publication - Create Source Prompt")
                .put(dialCoreUrl("/v1/prompts/" + bucket + "/" + promptName))
                .headers(Configs.DIAL_CORE_API_HEADERS)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder deletePublicationPrompt(String bucket, String promptName) {
        return http("Publication - Delete Source Prompt")
                .delete(dialCoreUrl("/v1/prompts/" + bucket + "/" + promptName))
                .headers(Configs.DIAL_CORE_API_HEADERS);
    }

    public static HttpRequestActionBuilder createPublication(String requestName, String name,
                                                              String targetFolder, String sourceUrl,
                                                              String targetUrl, String savePublicationUrlAs) {
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
                      "targets": ["default"]
                    }
                  ]
                }""".formatted(name, targetFolder, sourceUrl, targetUrl);

        return http(requestName)
                .post(dialCoreUrl("/v1/ops/publication/create"))
                .headers(Configs.DIAL_CORE_API_HEADERS)
                .body(StringBody(payload))
                .check(status().is(200))
                .check(jsonPath("$.url").saveAs(savePublicationUrlAs));
    }

    public static HttpRequestActionBuilder createRulesOnlyPublication(String requestName, String name,
                                                                       String targetFolder,
                                                                       String savePublicationUrlAs) {
        String payload = """
                {
                  "name": "%s",
                  "targetFolder": "%s",
                  "rules": [
                    {
                      "source": "roles",
                      "function": "EQUAL",
                      "targets": ["default"]
                    }
                  ]
                }""".formatted(name, targetFolder);

        return http(requestName)
                .post(dialCoreUrl("/v1/ops/publication/create"))
                .headers(Configs.DIAL_CORE_API_HEADERS)
                .body(StringBody(payload))
                .check(status().is(200))
                .check(jsonPath("$.url").saveAs(savePublicationUrlAs));
    }

    public static HttpRequestActionBuilder createUnpublishPublication(String targetFolder, String targetUrl,
                                                                       String savePublicationUrlAs) {
        String payload = """
                {
                  "name": "Unpublish performance resource",
                  "targetFolder": "%s",
                  "resources": [
                    {
                      "action": "DELETE",
                      "targetUrl": "%s"
                    }
                  ]
                }""".formatted(targetFolder, targetUrl);

        return http("Publication - Create Unpublish Request")
                .post(dialCoreUrl("/v1/ops/publication/create"))
                .headers(Configs.DIAL_CORE_API_HEADERS)
                .body(StringBody(payload))
                .check(status().is(200))
                .check(jsonPath("$.url").saveAs(savePublicationUrlAs));
    }

    public static HttpRequestActionBuilder getPublication(Map<String, String> headers, String publicationUrl) {
        return publicationUrlRequest("Publication - Get", "/v1/ops/publication/get", headers, publicationUrl);
    }

    public static HttpRequestActionBuilder listPublications(String requestName, Map<String, String> headers,
                                                            String publicationFolderUrl) {
        String payload = """
                {"url": "%s"}
                """.formatted(publicationFolderUrl);

        return http(requestName)
                .post(dialCoreUrl("/v1/ops/publication/list"))
                .headers(headers)
                .body(StringBody(payload))
                .check(status().is(200));
    }

    public static HttpRequestActionBuilder updatePublication(String publicationUrl, String name,
                                                              String targetFolder, String sourceUrl,
                                                              String targetUrl) {
        String payload = """
                {
                  "url": "%s",
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
                      "targets": ["default"]
                    }
                  ]
                }""".formatted(publicationUrl, name, targetFolder, sourceUrl, targetUrl);

        return http("Publication - Update")
                .post(dialCoreUrl("/v1/ops/publication/update"))
                .headers(Configs.DIAL_CORE_PUBLICATION_ADMIN_HEADERS)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder deletePublication(String publicationUrl) {
        return publicationUrlRequest("Publication - Delete Pending", "/v1/ops/publication/delete",
                Configs.DIAL_CORE_API_HEADERS, publicationUrl);
    }

    public static HttpRequestActionBuilder approvePublication(String requestName, String publicationUrl) {
        return publicationUrlRequest(requestName, "/v1/ops/publication/approve",
                Configs.DIAL_CORE_PUBLICATION_ADMIN_HEADERS, publicationUrl);
    }

    public static HttpRequestActionBuilder rejectPublication(String publicationUrl) {
        String payload = """
                {
                  "url": "%s",
                  "comment": "Rejected by publication performance scenario"
                }""".formatted(publicationUrl);

        return http("Publication - Reject")
                .post(dialCoreUrl("/v1/ops/publication/reject"))
                .headers(Configs.DIAL_CORE_PUBLICATION_ADMIN_HEADERS)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder getPublicationRules(Map<String, String> headers, String targetFolder) {
        String payload = """
                {"url": "%s"}
                """.formatted(targetFolder);

        return http("Publication - List Rules")
                .post(dialCoreUrl("/v1/ops/publication/rule/list"))
                .headers(headers)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder getPublishedPrompt(String targetPath) {
        return http("Publication - Get Published Prompt")
                .get(dialCoreUrl("/v1/prompts/public/" + targetPath))
                .headers(Configs.DIAL_CORE_PUBLICATION_ADMIN_HEADERS);
    }

    private static HttpRequestActionBuilder publicationUrlRequest(String requestName, String endpoint,
                                                                  Map<String, String> headers,
                                                                  String publicationUrl) {
        String payload = """
                {"url": "%s"}
                """.formatted(publicationUrl);

        return http(requestName)
                .post(dialCoreUrl(endpoint))
                .headers(headers)
                .body(StringBody(payload));
    }

    /*
    ***************************************************************
    * MCP container deployment requests (deployment-manager API)
    * Ported from scripts/DeployApp/run_mcp_container.py (+ workflow.py / api.py).
    *
    * These calls target the deploy-service host (PropertiesHolder.urlDeployService)
    * via absolute URLs, so they work regardless of the scenario's protocol baseUrl
    * (which stays on the Admin host for the Auth0 login flow). They save the created
    * resource ids into the session ("mcpImageId", "mcpDeploymentName") so subsequent
    * chains / tests can reuse them.
    ***************************************************************
    */

    /** Deploy-service base URL (absolute), trailing slash stripped. Empty until configured. */
    private static final String MCP_DEPLOY_HOST = PropertiesHolder.urlDeployService.replaceAll("/$", "");

    /** allowedDomains from constants.py (ALLOWED_DOMAINS). */
    private static final String MCP_ALLOWED_DOMAINS_JSON =
            "[\"toolbox-data.anchore.io\",\"production.cloudfront.docker.com\",\"ghcr.io\","
            + "\"pkg-containers.githubusercontent.com\",\"*\"]";

    public static HttpRequestActionBuilder createMcpImage(String imageName) {
        String payload = """
                {
                  "name": "%s",
                  "id": null,
                  "version": "1.0.0",
                  "description": "",
                  "source": {"$type": "docker", "imageUri": "%s"},
                  "buildStatus": "NOT_BUILT",
                  "allowedDomains": %s,
                  "imageBuilder": "BUILDKIT",
                  "$type": "mcp",
                  "transportType": "local"
                }""".formatted(imageName, PropertiesHolder.mcpDockerImage, MCP_ALLOWED_DOMAINS_JSON);

        return http("MCP - Create Image Definition")
                .post(MCP_DEPLOY_HOST + "/api/v1/images/definitions")
                .headers(Configs.MCP_DEPLOY_API_HEADERS)
                .body(StringBody(payload))
                .check(status().is(201))
                .check(jsonPath("$.id").saveAs("mcpImageId"));
    }

    public static HttpRequestActionBuilder buildMcpImage() {
        String payload = """
                {"imageDefinitionId": "#{mcpImageId}"}""";

        return http("MCP - Build Image")
                .post(MCP_DEPLOY_HOST + "/api/v1/images/builds")
                .headers(Configs.MCP_DEPLOY_API_HEADERS)
                .body(StringBody(payload))
                .check(status().is(201));
    }

    public static HttpRequestActionBuilder getMcpImageBuildStatus() {
        return http("MCP - Get Image Build Status")
                .get(MCP_DEPLOY_HOST + "/api/v1/images/builds/#{mcpImageId}/status")
                .headers(Configs.MCP_DEPLOY_API_HEADERS)
                .check(status().in(200, 202))
                .check(bodyString().saveAs("mcpBuildStatusBody"));
    }

    public static HttpRequestActionBuilder createMcpDeployment(String deploymentName) {
        String payload = """
                {
                  "name": "%s",
                  "displayName": "%s",
                  "version": "1.0.0",
                  "description": "",
                  "$type": "mcp",
                  "status": "not_deployed",
                  "source": {"$type": "internal_image", "imageDefinitionId": "#{mcpImageId}"},
                  "metadata": {"envs": []},
                  "scaling": {"minReplicas": 0, "maxReplicas": 1, "scaleToZeroDelaySeconds": 300},
                  "resources": {
                    "requests": {"cpu": "0.5", "memory": "1073741824"},
                    "limits": {"cpu": "0.5", "memory": "1073741824"}
                  },
                  "containerPort": null,
                  "transport": "http_streaming"
                }""".formatted(deploymentName, deploymentName);

        return http("MCP - Create Deployment")
                .post(MCP_DEPLOY_HOST + "/api/v1/deployments")
                .headers(Configs.MCP_DEPLOY_API_HEADERS)
                .body(StringBody(payload))
                .check(status().is(201))
                .check(jsonPath("$.name").saveAs("mcpDeploymentName"));
    }

    public static HttpRequestActionBuilder runMcpDeployment() {
        return http("MCP - Run Deployment")
                .post(MCP_DEPLOY_HOST + "/api/v1/deployments/#{mcpDeploymentName}/deploy")
                .headers(Configs.MCP_DEPLOY_API_HEADERS)
                .body(StringBody(""))
                .check(status().is(200));
    }

    public static HttpRequestActionBuilder getMcpDeploymentStatus() {
        return http("MCP - Get Deployment Status")
                .get(MCP_DEPLOY_HOST + "/api/v1/deployments/#{mcpDeploymentName}")
                .headers(Configs.MCP_DEPLOY_API_HEADERS)
                .check(jsonPath("$.status").saveAs("mcpDeploymentStatus"));
    }

    public static HttpRequestActionBuilder deleteMcpDeployment() {
        return http("MCP - Delete Deployment")
                .delete(MCP_DEPLOY_HOST + "/api/v1/deployments/#{mcpDeploymentName}")
                .headers(Configs.MCP_DEPLOY_API_HEADERS);
    }

    public static HttpRequestActionBuilder deleteMcpImage() {
        return http("MCP - Delete Image Definition")
                .delete(MCP_DEPLOY_HOST + "/api/v1/images/definitions/#{mcpImageId}")
                .headers(Configs.MCP_DEPLOY_API_HEADERS);
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

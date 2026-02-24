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
import java.util.Random;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class Requests {

    public static HttpRequestActionBuilder createModelAPI(String modelName) {
        String payload = "{\"name\":\"" + modelName + "\"," +
        "\"description\":\"\"," +
        "\"endpoint\":\"" + PropertiesHolder.modelEndpoint + "\"," +
        "\"displayVersion\":\"\"," +
        "\"displayName\":\"" + modelName + "\"," +
        "\"type\":\"chat\"}";

        return http("Create Model API")
                .post("api/v1/models")
                .headers(Configs.DIAL_ADMIN_API_HEADERS)
                .body(StringBody(payload));
    }

    public static HttpRequestActionBuilder getAllModelsAPI() {
        return http("Get all Models API")
                .get("api/v1/models")
                .headers(Configs.DIAL_ADMIN_API_HEADERS);
    }

    public static HttpRequestActionBuilder getModelDetailsAPI(String modelName) {
        return http("Get Model Details")
                .get("/api/v1/models/" + modelName)
                .headers(Configs.DIAL_ADMIN_API_HEADERS);
    }

    public static HttpRequestActionBuilder syncModelStateAPI(String modelName) {
        return http("Sync Model State")
                .get("api/v1/models/" + modelName + "/sync-state")
                .headers(Configs.DIAL_ADMIN_API_HEADERS)
                .check(bodyString().saveAs("syncModelStateAPIResponseBody"))
                .check(jsonPath("$.status").saveAs("syncModelStatus"));
    }

    public static HttpRequestActionBuilder dialAdminADAPIAuth() {
        return http("DialAdminADAuth")
                .post("https://login.microsoftonline.com/organizations/oauth2/v2.0/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .formParam("grant_type", "password")
                .formParam("scope", PropertiesHolder.DIAL_ADMIN_SCOPE)
                .formParam("client_secret", PropertiesHolder.DIAL_ADMIN_CLIENT_SECRET)
                .formParam("client_id", PropertiesHolder.DIAL_ADMIN_CLIENT_ID)
                .formParam("username", "#{username}")
                .formParam("password", "#{password}")
                .check(jsonPath("$.access_token").saveAs("dialAdminAccessToken"));
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

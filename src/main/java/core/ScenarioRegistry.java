package core;

import builders.Scenarios;
import io.gatling.javaapi.core.ScenarioBuilder;

import java.util.Map;
import java.util.function.Supplier;

import static core.PropertiesHolder.*;
import static java.util.Map.entry;

public class ScenarioRegistry {

    public static class ScenarioConfig {
        public final Supplier<ScenarioBuilder> scenario;
        public final String baseUrl;

        public ScenarioConfig(Supplier<ScenarioBuilder> scenario, String baseUrl) {
            this.scenario = scenario;
            this.baseUrl = baseUrl;
        }
    }

    private static final Map<String, ScenarioConfig> REGISTRY = Map.ofEntries(
        entry("aiDialAdminAuth0CreateModel", new ScenarioConfig(
            () -> Scenarios.aiDialAdminAuth0CreateModelScenario(modelSyncAttempts, modelSyncPauseDuration),
            aiAdminBaseUrl
        )),
        entry("aiDialAdminRequests", new ScenarioConfig(
            Scenarios::aiDialApplicationRequestsScenario,
            dialCoreBaseUrl
        )),
        entry("toolsetRequests", new ScenarioConfig(
            Scenarios::toolsetRequestsScenario,
            dialCoreBaseUrl
        )),
        entry("promptRequests", new ScenarioConfig(
            Scenarios::promptRequestsScenario,
            dialCoreBaseUrl
        )),
        entry("fileRequests", new ScenarioConfig(
            Scenarios::fileRequestsScenario,
            dialCoreBaseUrl
        )),
        entry("deploymentListing", new ScenarioConfig(
            Scenarios::deploymentListingScenario,
            dialCoreBaseUrl
        )),
        entry("sharingRequests", new ScenarioConfig(
            Scenarios::sharingRequestsScenario,
            dialCoreBaseUrl
        )),
        entry("perRequestPermissions", new ScenarioConfig(
            Scenarios::perRequestPermissionsScenario,
            dialCoreBaseUrl
        )),
        entry("publicationRequests", new ScenarioConfig(
            Scenarios::publicationRequestsScenario,
            dialCoreBaseUrl
        )),
        entry("publicationAdmin", new ScenarioConfig(
            Scenarios::publicationAdminScenario,
            dialCoreBaseUrl
        ))
    );

    public static ScenarioConfig get(String name) {
        ScenarioConfig config = REGISTRY.get(name);
        if (config == null) {
            throw new IllegalArgumentException(
                "Unknown scenario: '" + name + "'. Available scenarios: " + REGISTRY.keySet()
            );
        }
        return config;
    }
}

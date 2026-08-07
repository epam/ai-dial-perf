package core;

import builders.Scenarios;
import io.gatling.javaapi.core.ScenarioBuilder;

import java.util.Map;
import java.util.function.Supplier;

import static core.PropertiesHolder.*;

public class ScenarioRegistry {

    public static class ScenarioConfig {
        public final Supplier<ScenarioBuilder> scenario;
        public final Supplier<ScenarioBuilder> setupScenario;
        public final String baseUrl;

        public ScenarioConfig(Supplier<ScenarioBuilder> scenario, String baseUrl) {
            this(scenario, null, baseUrl);
        }

        public ScenarioConfig(Supplier<ScenarioBuilder> scenario,
                              Supplier<ScenarioBuilder> setupScenario,
                              String baseUrl) {
            this.scenario = scenario;
            this.setupScenario = setupScenario;
            this.baseUrl = baseUrl;
        }
    }

    private static final Map<String, ScenarioConfig> REGISTRY = Map.ofEntries(
        Map.entry("aiDialAdminAuth0CreateModel", new ScenarioConfig(
            () -> Scenarios.aiDialAdminAuth0CreateModelScenario(modelSyncAttempts, modelSyncPauseDuration),
            aiAdminBaseUrl
        )),
        Map.entry("createKeyWithRole", new ScenarioConfig(
            CreateKeyWithRoleScenario::build,
            aiAdminBaseUrl
        )),
        Map.entry("aiDialAdminRequests", new ScenarioConfig(
            Scenarios::aiDialApplicationRequestsScenario,
            dialCoreBaseUrl
        )),
        Map.entry("toolsetRequests", new ScenarioConfig(
            Scenarios::toolsetRequestsScenario,
            dialCoreBaseUrl
        )),
        Map.entry("promptRequests", new ScenarioConfig(
            Scenarios::promptRequestsScenario,
            dialCoreBaseUrl
        )),
        Map.entry("fileRequests", new ScenarioConfig(
            Scenarios::fileRequestsScenario,
            dialCoreBaseUrl
        )),
        Map.entry("deploymentListing", new ScenarioConfig(
            Scenarios::deploymentListingScenario,
            dialCoreBaseUrl
        )),
        Map.entry("sharingRequests", new ScenarioConfig(
            Scenarios::sharingRequestsScenario,
            dialCoreBaseUrl
        )),
        Map.entry("perRequestPermissions", new ScenarioConfig(
            Scenarios::perRequestPermissionsScenario,
            dialCoreBaseUrl
        )),
        Map.entry("publicationRequests", new ScenarioConfig(
            Scenarios::publicationRequestsScenario,
            aiAdminBaseUrl
        )),
        Map.entry("runMcpContainer", new ScenarioConfig(
            Scenarios::runMcpContainerScenario,
            aiAdminBaseUrl
        )),
        Map.entry("AdminCoreSystem", new ScenarioConfig(
            Scenarios::adminCoreSystemScenario,
            Scenarios::adminCoreSystemSetupScenario,
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

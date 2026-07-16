package core;

import builders.Scenarios;
import io.gatling.javaapi.core.ScenarioBuilder;

import java.util.Map;
import java.util.function.Supplier;

import static core.PropertiesHolder.*;

public class ScenarioRegistry {

    public static class ScenarioConfig {
        public final Supplier<ScenarioBuilder> scenario;
        public final String baseUrl;

        public ScenarioConfig(Supplier<ScenarioBuilder> scenario, String baseUrl) {
            this.scenario = scenario;
            this.baseUrl = baseUrl;
        }
    }

    private static final Map<String, ScenarioConfig> REGISTRY = Map.of(
        "aiDialAdminAuth0CreateModel", new ScenarioConfig(
            () -> Scenarios.aiDialAdminAuth0CreateModelScenario(modelSyncAttempts, modelSyncPauseDuration),
            aiAdminBaseUrl
        ),
        "aiDialAdminRequests", new ScenarioConfig(
            Scenarios::aiDialApplicationRequestsScenario,
            dialCoreBaseUrl
        ),
        "toolsetRequests", new ScenarioConfig(
            Scenarios::toolsetRequestsScenario,
            dialCoreBaseUrl
        ),
        "promptRequests", new ScenarioConfig(
            Scenarios::promptRequestsScenario,
            dialCoreBaseUrl
        ),
        "fileRequests", new ScenarioConfig(
            Scenarios::fileRequestsScenario,
            dialCoreBaseUrl
        )
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

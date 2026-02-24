import core.Configs;
import core.ScenarioRegistry;
import core.ScenarioRegistry.ScenarioConfig;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import static core.PropertiesHolder.*;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.http;

public class DebugSimulation extends Simulation {

    private static final ScenarioConfig config = ScenarioRegistry.get(scenarioName);

    {
        setUp(buildPopulation())
            .protocols(http.baseUrl(config.baseUrl))
            .assertions(Configs.SUCCESS_RATE_DEBUG_ASSERTION);
    }

    private PopulationBuilder buildPopulation() {
        ScenarioBuilder scn = config.scenario.get();

        if (openModel) {
            return scn.injectOpen(atOnceUsers(users));
        }

        return scn.injectClosed(constantConcurrentUsers(users).during(duration));
    }
}

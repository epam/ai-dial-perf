import core.Configs;
import core.ScenarioRegistry;
import core.ScenarioRegistry.ScenarioConfig;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import static core.PropertiesHolder.*;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.http;

public class LoadSimulation extends Simulation {

    private static final ScenarioConfig config = ScenarioRegistry.get(scenarioName);

    {
        setUp(buildPopulation())
            .protocols(http.baseUrl(config.baseUrl))
            .assertions(Configs.SUCCESS_RATE_LOAD_ASSERTION);
    }

    private PopulationBuilder buildPopulation() {
        ScenarioBuilder scn = config.scenario.get();

        if (openModel) {
            return scn.injectOpen(
                rampUsersPerSec(usersRampUpFrom).to(users).during(durationRampUp),
                constantUsersPerSec(users).during(duration),
                rampUsersPerSec(users).to(usersRampDownTo).during(durationRampDown)
            );
        }
        else {
            return scn.injectClosed(
                rampConcurrentUsers(usersRampUpFrom).to(users).during(duration),
                constantConcurrentUsers(users).during(duration),
                rampConcurrentUsers(users).to(usersRampDownTo).during(durationRampDown)
            );
        }
    }
}

package core;

import builders.Requests;
import builders.Scenarios;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;

import java.util.UUID;

import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Standalone performance scenario migrated from
 * {@code test_should_create_key_with_role}.
 */
public final class CreateKeyWithRoleScenario {

    private static final String ROLE_NAME = "default";

    private CreateKeyWithRoleScenario() {
    }

    public static ScenarioBuilder build() {
        return scenario("Create Key With Role")
                .exec(Scenarios.aiDialAdminAuth0UIAuthChain())
                .exec(createAndVerifyKey());
    }

    private static ChainBuilder createAndVerifyKey() {
        return exec(session -> session
                .set("roleName", ROLE_NAME)
                .set("keyName", "Key-" + UUID.randomUUID())
                .set("keyValue", UUID.randomUUID().toString())
                .set("project", "Project-" + UUID.randomUUID())
                .set("displayName", "DisplayName-" + UUID.randomUUID()))
                .exec(Requests.createKeyWithRoleAPI())
                .exec(Requests.getKeyAPI("#{keyName}")
                        .check(jsonPath("$.roles[0]").is(ROLE_NAME)));
    }
}

package gov.va.api.lighthouse.charon.tests;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import gov.va.api.lighthouse.charon.api.RpcRequest;
import gov.va.api.lighthouse.charon.api.RpcResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class PingIT {
  @Test
  void healthCheckIt() {
    // No test's running causes the entrypoint to fail.
    // In order to test when Vista is available and not fail when it isn't,
    // this will run in all environments.
    var basePath = "/charon/";
    log.warn("Running HealthCheck outside of local environment.");
    var requestPath = basePath + "actuator/health";
    log.info("Running health-check for path: {}", requestPath);
    TestClients.charon().get(requestPath).response().then().body("status", equalTo("UP"));
  }

  @Test
  @SneakyThrows
  void requestRpcNoArguments() {
    var systemDefinition = SystemDefinitions.get();
    assumeTrue(systemDefinition.isVistaAvailable(), "Vista is unavailable.");
    RpcRequest body =
        RpcRequest.builder()
            .rpc(systemDefinition.testRpcs().pingRpc())
            .principal(systemDefinition.testRpcPrincipal())
            .target(systemDefinition.testTargets())
            .build();
    var response =
        TestClients.rpcRequest(systemDefinition.charon().apiPath() + "rpc", body)
            .expect(200)
            .expectValid(RpcResponse.class);
    log.info(response.toString());
  }
}
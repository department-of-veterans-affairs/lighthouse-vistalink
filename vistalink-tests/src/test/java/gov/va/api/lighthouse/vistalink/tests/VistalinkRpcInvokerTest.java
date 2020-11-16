package gov.va.api.lighthouse.vistalink.tests;

import static org.assertj.core.api.Assumptions.assumeThat;

import gov.va.api.health.autoconfig.configuration.JacksonConfig;
import gov.va.api.lighthouse.vistalink.service.api.RpcDetails;
import gov.va.api.lighthouse.vistalink.service.api.RpcInvocationResult;
import gov.va.api.lighthouse.vistalink.service.api.RpcPrincipal;
import gov.va.api.lighthouse.vistalink.service.config.ConnectionDetails;
import gov.va.api.lighthouse.vistalink.service.controller.VistalinkRpcInvokerFactory;
import java.util.Locale;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.junit.jupiter.api.Test;

@Slf4j
public class VistalinkRpcInvokerTest {

  VistalinkTestConfig config;
  VistalinkRpcInvokerFactory vistalinkRpcInvokerFactory;

  @Test
  @SneakyThrows
  void invoke() {
    assumeThat(isEnabled())
        .as(
            "Set system property 'test.rpcinvoker' or environment variable 'TEST_RPCINVOKER' to true to enable.")
        .isTrue();
    config = VistalinkTestConfig.fromSystemProperties();
    vistalinkRpcInvokerFactory = new VistalinkRpcInvokerFactory();
    var rpcPrincipal =
        RpcPrincipal.builder().accessCode(config.accessCode).verifyCode(config.verifyCode).build();
    var connectionDetails =
        ConnectionDetails.builder()
            .divisionIen(config.divisionIen)
            .port(Integer.parseInt(config.port))
            .host(config.host)
            .name(config.name)
            .build();
    var vistalinkRpcInvoker = vistalinkRpcInvokerFactory.create(rpcPrincipal, connectionDetails);
    var rpc = JacksonConfig.createMapper().readValue(config.rpc, RpcDetails.class);
    RpcInvocationResult result = vistalinkRpcInvoker.invoke(rpc);
    log.info("RESULT\n---\n{}\n---", result.response());
  }

  boolean isEnabled() {
    String enabled = System.getProperty("test.rpcinvoker");
    if (enabled == null || enabled.isBlank()) {
      enabled = System.getenv("TEST_RPCINVOKER");
    }
    return BooleanUtils.toBoolean(enabled);
  }

  @Value
  @Builder
  static class VistalinkTestConfig {
    String accessCode;
    String verifyCode;
    String divisionIen;
    String host;
    String port;
    String name;
    String rpc;

    @SneakyThrows
    static VistalinkTestConfig fromSystemProperties() {
      String host = propertyOrDie("host");

      String defaultRpc =
          JacksonConfig.createMapper()
              .writeValueAsString(
                  RpcDetails.builder()
                      .name("XOBV TEST PING")
                      .context("XOBV VISTALINK TESTER")
                      .build());

      return VistalinkTestConfig.builder()
          .accessCode(propertyOrDie("access-code"))
          .verifyCode(propertyOrDie("verify-code"))
          .divisionIen(propertyOrDie("division-ien"))
          .host(host)
          .port(propertyOrDie("port"))
          .name(propertyOrDie("name", "vista:" + host))
          .rpc(propertyOrDie("rpc", defaultRpc))
          .build();
    }

    private static String propertyOrDie(String suffix) {
      return propertyOrDie(suffix, null);
    }

    private static String propertyOrDie(String suffix, String defaultValue) {
      var propertyName = "vista." + suffix;
      var environmentVariableName =
          propertyName.toUpperCase(Locale.ENGLISH).replace('.', '_').replace('-', '_');
      var value = System.getProperty(propertyName);
      if (value == null || value.isBlank()) {
        value = System.getenv(environmentVariableName);
      }
      if (value == null || value.isBlank()) {
        value = defaultValue;
      }
      if (value == null || value.isBlank()) {
        throw new IllegalStateException(
            "Missing system property '"
                + propertyName
                + "' or environment variable '"
                + environmentVariableName
                + "'");
      }
      return value;
    }
  }
}
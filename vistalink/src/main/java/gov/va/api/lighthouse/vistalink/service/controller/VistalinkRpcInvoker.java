package gov.va.api.lighthouse.vistalink.service.controller;

import gov.va.api.lighthouse.vistalink.service.api.RpcDetails;
import gov.va.api.lighthouse.vistalink.service.api.RpcInvocationResult;
import gov.va.api.lighthouse.vistalink.service.api.RpcPrincipal;
import gov.va.api.lighthouse.vistalink.service.config.ConnectionDetails;
import gov.va.med.vistalink.adapter.cci.VistaLinkConnection;
import gov.va.med.vistalink.rpc.RpcRequest;
import gov.va.med.vistalink.rpc.RpcRequestFactory;
import gov.va.med.vistalink.rpc.RpcResponse;
import gov.va.med.vistalink.security.CallbackHandlerUnitTest;
import gov.va.med.vistalink.security.VistaKernelPrincipalImpl;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VistalinkRpcInvoker implements RpcInvoker {

  private final RpcPrincipal rpcPrincipal;

  private final ConnectionDetails connectionDetails;

  private final CallbackHandler handler;

  private final LoginContext loginContext;

  private final VistaKernelPrincipalImpl kernelPrincipal;

  private final VistaLinkConnection connection;

  @Builder
  VistalinkRpcInvoker(RpcPrincipal rpcPrincipal, ConnectionDetails connectionDetails) {
    this.rpcPrincipal = rpcPrincipal;
    this.connectionDetails = connectionDetails;
    this.handler = createLoginCallbackHandler();
    this.loginContext = createLoginContext();
    this.kernelPrincipal = createVistaKernelPrincipal();
    this.connection = createConnection();
  }

  @Override
  public void close() {
    try {
      loginContext.logout();
    } catch (LoginException e) {
      log.warn("Failed to logout", e);
    }
  }

  private VistaLinkConnection createConnection() {
    return kernelPrincipal.getAuthenticatedConnection();
  }

  private CallbackHandler createLoginCallbackHandler() {
    /*
     * There are only two CallbackHandlers that will work. This one, and one for Swing applications.
     * All of the internals for working with Vistalink callbacks are _package_ protected so we
     * cannot create our own, e.g. CallbackChangeVc. Despite the "UnitTest" name, decompiled code
     * reveals that this handler is simply update the VC callback objects with access code, verify
     * code, and division IEN as appropriate. I do not see any "unit test" behavior in the handler.
     * It would have been better named "UnattendedCallbackHandler".
     */
    return new CallbackHandlerUnitTest(
        rpcPrincipal.accessCode(), rpcPrincipal.verifyCode(), connectionDetails.divisionIen());
  }

  @SneakyThrows
  private LoginContext createLoginContext() {
    Configuration jaasConfiguration =
        new Configuration() {
          @Override
          public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
            return new AppConfigurationEntry[] {
              new AppConfigurationEntry(
                  "gov.va.med.vistalink.security.VistaLoginModule",
                  LoginModuleControlFlag.REQUISITE,
                  Map.of(
                      "gov.va.med.vistalink.security.ServerAddressKey",
                      connectionDetails.host(),
                      "gov.va.med.vistalink.security.ServerPortKey",
                      connectionDetails.port()))
            };
          }
        };
    // TODO: make LoginContext name unique?
    return new LoginContext("vlx:" + connectionDetails.host(), null, handler, jaasConfiguration);
  }

  @SneakyThrows
  private VistaKernelPrincipalImpl createVistaKernelPrincipal() {
    loginContext.login();
    return VistaKernelPrincipalImpl.getKernelPrincipal(loginContext.getSubject());
  }

  /** Invoke an RPC with raw types. */
  @SneakyThrows
  public RpcResponse invoke(RpcRequest request) {
    return connection.executeRPC(request);
  }

  // TODO
  @Override
  @SneakyThrows
  public RpcInvocationResult invoke(RpcDetails rpcDetails) {
    var start = Instant.now();
    try {
      var vistalinkRequest = RpcRequestFactory.getRpcRequest();
      vistalinkRequest.setRpcContext(rpcDetails.context());
      vistalinkRequest.setUseProprietaryMessageFormat(true);
      vistalinkRequest.setRpcName(rpcDetails.name());
      if (rpcDetails.version().isPresent()) {
        vistalinkRequest.setRpcVersion(rpcDetails.version().get());
      }
      for (int i = 0; i < rpcDetails.parameters().size(); i++) {
        var parameter = rpcDetails.parameters().get(i);
        vistalinkRequest.getParams().setParam(i + 1, parameter.type(), parameter.value());
      }
      RpcResponse vistalinkResponse = invoke(vistalinkRequest);
      log.info("Response: " + vistalinkResponse.getRawResponse());
      // TODO: Turn XML into RpcInvocationResults for now, return nothing.
      return RpcInvocationResult.builder().build();
    } finally {
      log.info(
          "{} ms for {}", Duration.between(start, Instant.now()).toMillis(), rpcDetails.name());
    }
  }

  @Override
  public String vista() {
    return connectionDetails.name();
  }
}

package com.amazonaws.accessanalyzer.analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.val;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.accessanalyzer.AccessAnalyzerClient;
import software.amazon.awssdk.services.accessanalyzer.model.AccessDeniedException;
import software.amazon.awssdk.services.accessanalyzer.model.AnalyzerSummary;
import software.amazon.awssdk.services.accessanalyzer.model.ListAnalyzersRequest;
import software.amazon.awssdk.services.accessanalyzer.model.ListAnalyzersResponse;
import software.amazon.awssdk.services.accessanalyzer.model.Type;
import software.amazon.awssdk.services.accessanalyzer.paginators.ListAnalyzersIterable;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

class ListHandlerTest {

  private static List<String> TEST_NAMES = Arrays.asList("A1", "A2", "A3");
  private static List<String> TEST_NAMES2 = Arrays.asList("B1", "B2", "B3", "B4");
  private static Credentials PROXY_CREDS = new Credentials("PROXY_KEY", "PROXY_SECRET", "PROXY_SESSION");

  @AllArgsConstructor
  private static class ConstantClient implements AccessAnalyzerClient {

    private final List<AnalyzerSummary> analyzers;

    @Override
    public ListAnalyzersIterable listAnalyzersPaginator(ListAnalyzersRequest listAnalyzersRequest)
        throws AwsServiceException, SdkClientException {
      return new ListAnalyzersIterable(this, listAnalyzersRequest);
    }

    @Override
    public ListAnalyzersResponse listAnalyzers(ListAnalyzersRequest listAnalyzersRequest)
        throws AwsServiceException, SdkClientException {
      return ListAnalyzersResponse.builder().analyzers(analyzers).build();
    }

    @Override
    public String serviceName() {
      return "constant-access-analyzer";
    }

    @Override
    public void close() {
    }
  }

  @AllArgsConstructor
  private static class PaginationClient implements AccessAnalyzerClient {

    private final List<AnalyzerSummary> analyzers1;
    private final List<AnalyzerSummary> analyzers2;


    @Override
    public ListAnalyzersIterable listAnalyzersPaginator(ListAnalyzersRequest listAnalyzersRequest)
        throws AwsServiceException, SdkClientException {
      return new ListAnalyzersIterable(this, listAnalyzersRequest);
    }

    @Override
    public ListAnalyzersResponse listAnalyzers(ListAnalyzersRequest listAnalyzersRequest)
        throws AwsServiceException, SdkClientException {
      val creds = listAnalyzersRequest.overrideConfiguration()
          .flatMap(AwsRequestOverrideConfiguration::credentialsProvider)
          .get()
          .resolveCredentials();
      // We want to be sure the credentials handed to our handler by the proxy are used in repeat
      // calls
      if (!equalCreds(PROXY_CREDS, creds)) {
        throw new RuntimeException("Credential mismatch");
      }
      if (listAnalyzersRequest.nextToken() == null) {
        return ListAnalyzersResponse.builder().analyzers(analyzers1).nextToken("foo").build();
      } else {
        return ListAnalyzersResponse.builder().analyzers(analyzers2).build();
      }
    }

    @Override
    public String serviceName() {
      return "pagination-access-analyzer";
    }

    @Override
    public void close() {
    }
  }

  @AllArgsConstructor
  private static class ThrowingClient implements AccessAnalyzerClient {

    private AwsServiceException exn;

    @Override
    public ListAnalyzersIterable listAnalyzersPaginator(ListAnalyzersRequest listAnalyzersRequest)
        throws AwsServiceException, SdkClientException {
      return new ListAnalyzersIterable(this, listAnalyzersRequest);
    }

    @Override
    public ListAnalyzersResponse listAnalyzers(ListAnalyzersRequest listAnalyzersRequest)
        throws AwsServiceException, SdkClientException {
      throw exn;
    }

    @Override
    public String serviceName() {
      return "throwing-access-analyzer";
    }

    @Override
    public void close() {
    }
  }


  private AmazonWebServicesClientProxy proxy = new AmazonWebServicesClientProxy(new LoggerProxy(),
      PROXY_CREDS, () -> 10L);
  private Logger logger = new LoggerProxy();

  @Test
  void testSimpleSuccess() {
    val summaries = Util.map(TEST_NAMES, ListHandlerTest::summaryFromName);
    val client = new ConstantClient(summaries);
    val response = invokeListHandler(client);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    assertThat(response.getCallbackContext()).isNull();
    assertThat(response.getResourceModels()).isNotNull();
    assertThat(response.getResourceModel()).isNull();
    assertThat(response.getMessage()).isNull();
    assertThat(response.getErrorCode()).isNull();
    assertThat(response.getNextToken()).isNull();
    verifyAgainstExpectedSummaries(summaries, response.getResourceModels().stream()
        .map(ResourceModel::getAnalyzerName)
        .collect(Collectors.toList()));
  }

  @Test
  void testEmptyResult() {
    val client = new ConstantClient(Collections.emptyList());
    val response = invokeListHandler(client);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    assertThat(response.getCallbackContext()).isNull();
    assertThat(response.getResourceModels().isEmpty()).isTrue();
    assertThat(response.getResourceModel()).isNull();
    assertThat(response.getMessage()).isNull();
    assertThat(response.getErrorCode()).isNull();
    assertThat(response.getNextToken()).isNull();
  }

  @Test
  void testPagination() {
    val summaries1 = Util.map(TEST_NAMES, ListHandlerTest::summaryFromName);
    val summaries2 = Util.map(TEST_NAMES2, ListHandlerTest::summaryFromName);
    val client = new PaginationClient(summaries1, summaries2);
    val response = invokeListHandler(client);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    assertThat(response.getCallbackContext()).isNull();
    assertThat(response.getResourceModels()).isNotNull();
    assertThat(response.getResourceModel()).isNull();
    assertThat(response.getMessage()).isNull();
    assertThat(response.getErrorCode()).isNull();
    assertThat(response.getNextToken()).isNull();
    verifyAgainstExpectedSummaries(Util.concat(summaries1, summaries2),
        response.getResourceModels().stream()
            .map(ResourceModel::getAnalyzerName)
            .collect(Collectors.toList()));
  }

  @Test
  void testAccessDeniedServiceException() {
    val client = new ThrowingClient(
        AccessDeniedException.builder().message("access denied").build());
    val response = invokeListHandler(client);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getResourceModel()).isNull();
    assertThat(response.getMessage()).startsWith("access denied");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.AccessDenied);
  }

  @Test
  void testUnknownServiceException() {
    val client = new ThrowingClient(
        AwsServiceException.builder().message("internal failure").build());
    val response = invokeListHandler(client);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getResourceModel()).isNull();
    assertThat(response.getMessage()).startsWith("internal failure");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceInternalError);
  }

  private ProgressEvent<ResourceModel, CallbackContext> invokeListHandler(
      AccessAnalyzerClient client) {
    val model = ResourceModel.builder().build();
    val context = new CallbackContext();
    ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(model)
        .build();
    return ListHandler.handleRequestWithClient(client, proxy, request, context, logger);
  }

  private static void verifyAgainstExpectedSummaries(List<AnalyzerSummary> expected,
      List<String> actual) {
    List<String> expectedAnalyzerNames = Util.map(expected, AnalyzerSummary::name);
    assertThat(actual).isEqualTo(expectedAnalyzerNames);
  }

  private static AnalyzerSummary summaryFromName(String name) {
    return AnalyzerSummary.builder().name(name).type(Type.ACCOUNT).build();
  }

  private static boolean equalCreds(Credentials creds1, AwsCredentials creds2a) {
    AwsSessionCredentials creds2 = (AwsSessionCredentials) creds2a;
    return creds1.getAccessKeyId().equals(creds2.accessKeyId()) &&
        creds1.getSecretAccessKey().equals(creds2.secretAccessKey()) &&
        creds1.getSessionToken().equals(creds2.sessionToken());
  }
}

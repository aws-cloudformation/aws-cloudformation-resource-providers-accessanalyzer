package com.amazonaws.accessanalyzer.analyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.amazonaws.AmazonServiceException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import software.amazon.awssdk.services.accessanalyzer.model.AnalyzerSummary;
import software.amazon.awssdk.services.accessanalyzer.model.ListAnalyzersRequest;
import software.amazon.awssdk.services.accessanalyzer.model.ListAnalyzersResponse;
import software.amazon.awssdk.services.accessanalyzer.model.Type;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

class ListHandlerTest {

  private List<String> TEST_NAMES = Arrays
      .asList("test-analyzer-1", "test-analyzer-2", "test-analyzer-3");
  private String TEST_NEXT_TOKEN = "next-analyzer-name";
  private String NO_NEXT_TOKEN = null;

  @Mock
  private AmazonWebServicesClientProxy proxy;

  @Mock
  private Logger logger;

  @BeforeEach
  void setup() {
    proxy = mock(AmazonWebServicesClientProxy.class);
    logger = mock(Logger.class);
  }

  @Test
  void testSimpleSuccess() {
    val summaries = generateTestAnalyzerSummaries();
    val listResult = ListAnalyzersResponse.builder().analyzers(summaries).build();
    doReturn(listResult)
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(ListAnalyzersRequest.class), any());

    val response = invokeListHandler();

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
    val listResult = ListAnalyzersResponse.builder().analyzers(Collections.emptyList()).build();
    doReturn(listResult)
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(ListAnalyzersRequest.class), any());
    val response = invokeListHandler();
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
    val summaries = generateTestAnalyzerSummaries();
    val listResultWithNextToken = ListAnalyzersResponse.builder().analyzers(summaries).nextToken(TEST_NEXT_TOKEN).build();
    val listResultWithoutNextToken = ListAnalyzersResponse.builder().analyzers(summaries).nextToken(null).build();
    doReturn(listResultWithNextToken, listResultWithoutNextToken)
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(ListAnalyzersRequest.class), any());
    val firstResponse = invokeListHandler();
    val secondResponse = invokeListHandler(firstResponse.getNextToken());
    assertThat(firstResponse.getNextToken()).isEqualTo(TEST_NEXT_TOKEN);
    assertThat(secondResponse.getNextToken()).isNull();
  }

  @Test
  void testUnknownServiceException() {
    doThrow(new AmazonServiceException("internal failure"))
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(), any());
    val response = invokeListHandler();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getResourceModel()).isNull();
    assertThat(response.getMessage()).startsWith("internal failure");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceInternalError);
  }

  private List<AnalyzerSummary> generateTestAnalyzerSummaries() {
    return TEST_NAMES.stream()
        .map(name -> AnalyzerSummary.builder().name(name).type(Type.ACCOUNT).build())
        .collect(Collectors.toList());
  }

  private ProgressEvent<ResourceModel, CallbackContext> invokeListHandler() {
    return invokeListHandler(NO_NEXT_TOKEN);
  }

  private ProgressEvent<ResourceModel, CallbackContext> invokeListHandler(String nextToken) {
    val model = ResourceModel.builder().build();
    val handler = new ListHandler();
    val context = new CallbackContext();
    ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(model)
        .nextToken(nextToken)
        .build();
    return handler.handleRequest(proxy, request, context, logger);
  }

  private void verifyAgainstExpectedSummaries(List<AnalyzerSummary> expected,
      List<String> actual) {
    List<String> expectedAnalyzerNames = expected.stream()
        .map(AnalyzerSummary::name).collect(Collectors.toList());
    assertThat(actual).isEqualTo(expectedAnalyzerNames);
  }
}

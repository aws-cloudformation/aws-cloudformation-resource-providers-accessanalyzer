package com.amazonaws.accessanalyzer.analyzer;

import static com.amazonaws.accessanalyzer.analyzer.TestUtil.ANALYZER_ARN;
import static com.amazonaws.accessanalyzer.analyzer.TestUtil.ANALYZER_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.amazonaws.AmazonServiceException;
import java.util.Collections;
import lombok.val;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import org.mockito.Mock;
import software.amazon.awssdk.services.accessanalyzer.model.AccessDeniedException;
import software.amazon.awssdk.services.accessanalyzer.model.AnalyzerSummary;
import software.amazon.awssdk.services.accessanalyzer.model.GetAnalyzerRequest;
import software.amazon.awssdk.services.accessanalyzer.model.GetAnalyzerResponse;
import software.amazon.awssdk.services.accessanalyzer.model.ListArchiveRulesRequest;
import software.amazon.awssdk.services.accessanalyzer.model.ListArchiveRulesResponse;
import software.amazon.awssdk.services.accessanalyzer.model.ResourceNotFoundException;
import software.amazon.awssdk.services.accessanalyzer.model.AnalyzerConfiguration;
import software.amazon.awssdk.services.accessanalyzer.model.UnusedAccessConfiguration;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

class ReadHandlerTest {
  private static final int TEST_UNSUED_ACCESS_AGE = 60;
  @Mock
  private AmazonWebServicesClientProxy proxy;

  @Mock
  private Logger logger;

  @BeforeEach
  void setup() {
    proxy = mock(AmazonWebServicesClientProxy.class);
    logger = mock(Logger.class);
  }

  @ParameterizedTest
  @MethodSource("provideAnalyzerType")
  void testBasicSuccess(final String analyzerType) {
    AnalyzerSummary.Builder analyzerSummaryBuilder = AnalyzerSummary.builder().arn(ANALYZER_ARN).type(analyzerType);
    if (analyzerType.equals(TestUtil.ACCOUNT_UNUSED_ACCESS)) {
      analyzerSummaryBuilder.configuration(
          AnalyzerConfiguration.builder()
              .unusedAccess(UnusedAccessConfiguration.builder()
                  .unusedAccessAge(TEST_UNSUED_ACCESS_AGE)
                  .build())
              .build()
      );
    }

    GetAnalyzerResponse getResponse = GetAnalyzerResponse.builder().analyzer(analyzerSummaryBuilder.build()).build();
    val listRulesResponse = ListArchiveRulesResponse.builder().archiveRules(Collections.emptyList()).build();
    doReturn(getResponse)
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(GetAnalyzerRequest.class), any());
    doReturn(listRulesResponse)
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(ListArchiveRulesRequest.class), any());
    val model = ResourceModel.builder().arn(ANALYZER_ARN).build();
    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(model)
        .build();
    val response = new ReadHandler()
        .handleRequest(proxy, request, new CallbackContext(), logger);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    assertThat(response.getCallbackContext()).isNull();
    Assertions.assertThat(response.getResourceModels()).isNull();
    assertThat(response.getResourceModel().getAnalyzerName()).isEqualTo(ANALYZER_NAME);
    assertThat(response.getResourceModel().getArn()).isEqualTo(ANALYZER_ARN);
    assertThat(response.getMessage()).isNull();
    assertThat(response.getErrorCode()).isNull();
    if (analyzerType.equals(TestUtil.ACCOUNT_UNUSED_ACCESS)) {
      assertThat(response.getResourceModel().getAnalyzerConfiguration().getUnusedAccessConfiguration().getUnusedAccessAge() == TEST_UNSUED_ACCESS_AGE);
    }
  }

  static Stream<Arguments> provideAnalyzerType() {
    return Stream.of(
        Arguments.of(TestUtil.ACCOUNT),
        Arguments.of(TestUtil.ACCOUNT_UNUSED_ACCESS)
    );
  }

  @Test
  void testNameIsNull() {
    val model = ResourceModel.builder().analyzerName(null).build();
    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(model)
        .build();
    val response = new ReadHandler().handleRequest(proxy, request, new CallbackContext(), logger);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getCallbackContext()).isNull();
    assertThat(response.getResourceModels()).isNull();
    assertThat(response.getResourceModel()).isEqualTo(model);
    assertThat(response.getMessage()).startsWith("Internal error");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InternalFailure);
  }

  @Test
  void testNameIsEmpty() {
    val model = ResourceModel.builder().analyzerName(null).build();
    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(model)
        .build();
    val response = new ReadHandler().handleRequest(proxy, request, new CallbackContext(), logger);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getCallbackContext()).isNull();
    assertThat(response.getResourceModels()).isNull();
    assertThat(response.getResourceModel()).isEqualTo(model);
    assertThat(response.getMessage()).startsWith("Internal error");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InternalFailure);
  }

  @Test
  void testNonExistentAnalyzer() {
    doThrow(ResourceNotFoundException.builder().message("").build())
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(GetAnalyzerRequest.class), any());
    val model = ResourceModel.builder().arn(ANALYZER_ARN).build();
    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(model)
        .build();
    val response = new ReadHandler().handleRequest(proxy, request, new CallbackContext(), logger);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getCallbackContext()).isNull();
    assertThat(response.getResourceModels()).isNull();
    assertThat(response.getResourceModel()).isEqualTo(model);
    assertThat(response.getMessage()).startsWith(ReadHandler.NO_ANALYZER_MESSAGE_PREFIX);
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
  }

  @Test
  void testAmazonServiceException() {
    doThrow(new AmazonServiceException("internal failure"))
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(), any());
    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(ResourceModel.builder().arn(ANALYZER_ARN).build()).build();
    val response = new ReadHandler()
        .handleRequest(proxy, request, new CallbackContext(), logger);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getResourceModel()).isNull();
    assertThat(response.getMessage()).startsWith("internal failure");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceInternalError);
  }

  @Test
  void testAccessDeniedException() {
    doThrow(AccessDeniedException.builder().message("access denied").build())
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(), any());
    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(ResourceModel.builder().arn(ANALYZER_ARN).build()).build();
    val response = new ReadHandler()
        .handleRequest(proxy, request, new CallbackContext(), logger);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getResourceModel()).isNull();
    assertThat(response.getMessage()).startsWith("access denied");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.AccessDenied);
  }
}

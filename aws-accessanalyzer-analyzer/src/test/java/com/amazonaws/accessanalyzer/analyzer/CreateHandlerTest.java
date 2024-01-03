package com.amazonaws.accessanalyzer.analyzer;

import static com.amazonaws.accessanalyzer.analyzer.TestUtil.ANALYZER_ARN;
import static com.amazonaws.accessanalyzer.analyzer.TestUtil.ANALYZER_NAME;
import static com.amazonaws.accessanalyzer.analyzer.TestUtil.CLIENT_REQUEST_TOKEN;
import static com.amazonaws.accessanalyzer.analyzer.TestUtil.LOGICAL_RESOURCE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.amazonaws.AmazonServiceException;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import org.mockito.Mock;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.accessanalyzer.model.AccessDeniedException;
import software.amazon.awssdk.services.accessanalyzer.model.CreateAnalyzerRequest;
import software.amazon.awssdk.services.accessanalyzer.model.CreateAnalyzerResponse;
import software.amazon.awssdk.services.accessanalyzer.model.ServiceQuotaExceededException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

class CreateHandlerTest {

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
  @MethodSource("provideCreateAnalyzerTestData")
  void testCreateAnalyzer(final String analyzerType) {
    // The desired state does not mention a name or ARN.  They are created from the
    // client request token and logical resource id.
    ResourceModel.ResourceModelBuilder stateBuilder = ResourceModel.builder()
        .type(analyzerType);
    if (analyzerType.equals(TestUtil.ACCOUNT_UNUSED_ACCESS)) {
      stateBuilder.analyzerConfiguration(AnalyzerConfiguration.builder()
          .unusedAccessConfiguration(UnusedAccessConfiguration.builder()
              .unusedAccessAge(60)
              .build())
          .build());
    }
    ResourceModel desiredState = stateBuilder.build();

    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .logicalResourceIdentifier(LOGICAL_RESOURCE_ID)
        .clientRequestToken(CLIENT_REQUEST_TOKEN)
        .desiredResourceState(desiredState)
        .build();
    val Response = CreateAnalyzerResponse.builder().arn(ANALYZER_ARN).build();
    doReturn(Response).when(proxy).injectCredentialsAndInvokeV2(any(CreateAnalyzerRequest.class), any());
    val response = invokeHandleRequest(request);
    val actualState = response.getResourceModel();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    assertThat(response.getCallbackContext()).isNull();
    assertThat(response.getResourceModels()).isNull();
    assertThat(response.getResourceModel()).isEqualTo(desiredState);
    assertThat(response.getMessage()).isNull();
    assertThat(response.getErrorCode()).isNull();
    assertThat(actualState.getAnalyzerName()).isEqualTo(desiredState.getAnalyzerName());
    assertThat(actualState.getArn()).isEqualTo(desiredState.getArn());
    assertThat(actualState.getType()).isEqualTo(desiredState.getType());
    if (analyzerType.equals(TestUtil.ACCOUNT_UNUSED_ACCESS)) {
      assertThat(actualState.getAnalyzerConfiguration().getUnusedAccessConfiguration().getUnusedAccessAge() ==
                 desiredState.getAnalyzerConfiguration().getUnusedAccessConfiguration().getUnusedAccessAge());
    }
  }

  static Stream<Arguments> provideCreateAnalyzerTestData() {
    return Stream.of(
        Arguments.of(TestUtil.ACCOUNT),
        Arguments.of(TestUtil.ACCOUNT_UNUSED_ACCESS)
    );
  }

  @ParameterizedTest
  @MethodSource("provideCreateAnalyzerTestData")
  void testLimitExceededServiceException(final String analyzerType) {
    doThrow(ServiceQuotaExceededException.builder().message("too many analyzers for account").build())
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(), any());
    val desiredState = ResourceModel.builder()
        .arn(ANALYZER_ARN)
        .analyzerName(ANALYZER_NAME)
        .type(analyzerType)
        .build();
    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .logicalResourceIdentifier(LOGICAL_RESOURCE_ID)
        .clientRequestToken(CLIENT_REQUEST_TOKEN)
        .desiredResourceState(desiredState)
        .build();
    val response = invokeHandleRequest(request);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getCallbackContext()).isNull();
    assertThat(response.getResourceModels()).isNull();
    assertThat(response.getResourceModel()).isNull();
    assertThat(response.getMessage()).startsWith("too many analyzers for account");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceLimitExceeded);
  }

  @ParameterizedTest
  @MethodSource("provideCreateAnalyzerTestData")
  void testUnknownServiceException(final String analyzerType) {
    doThrow(new AmazonServiceException("internal failure"))
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(), any());
    val desiredState = ResourceModel.builder()
        .arn(ANALYZER_ARN)
        .analyzerName(ANALYZER_NAME)
        .type(analyzerType)
        .build();
    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .logicalResourceIdentifier(LOGICAL_RESOURCE_ID)
        .clientRequestToken(CLIENT_REQUEST_TOKEN)
        .desiredResourceState(desiredState)
        .build();
    val response = invokeHandleRequest(request);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getResourceModel()).isNull();
    assertThat(response.getMessage()).startsWith("internal failure");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceInternalError);
  }

  @ParameterizedTest
  @MethodSource("provideCreateAnalyzerTestData")
  void testAccessDeniedException(final String analyzerType) {
    doThrow(AccessDeniedException.builder().message("access denied").build())
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(), any());
    val desiredState = ResourceModel.builder()
        .arn(ANALYZER_ARN)
        .analyzerName(ANALYZER_NAME)
        .type(analyzerType)
        .build();
    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .logicalResourceIdentifier(LOGICAL_RESOURCE_ID)
        .clientRequestToken(CLIENT_REQUEST_TOKEN)
        .desiredResourceState(desiredState)
        .build();
    val response = invokeHandleRequest(request);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getResourceModel()).isNull();
    assertThat(response.getMessage()).startsWith("access denied");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.AccessDenied);
  }

  @ParameterizedTest
  @MethodSource("provideCreateAnalyzerTestData")
  void testUnknownException(final String analyzerType) {
    doThrow(SdkServiceException.builder().message("Unknown Exception").build())
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(), any());
    val desiredState = ResourceModel.builder()
        .arn(ANALYZER_ARN)
        .analyzerName(ANALYZER_NAME)
        .type(analyzerType)
        .build();
    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .logicalResourceIdentifier(LOGICAL_RESOURCE_ID)
        .clientRequestToken(CLIENT_REQUEST_TOKEN)
        .desiredResourceState(desiredState)
        .build();
    val response = invokeHandleRequest(request);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getResourceModel()).isNull();
    assertThat(response.getMessage()).startsWith("Unknown Exception");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceInternalError);
  }

  @Test
  void testNullArnInResponse() {
    ResourceModel desiredState = ResourceModel.builder()
        .type(TestUtil.ACCOUNT).build();

    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .logicalResourceIdentifier(LOGICAL_RESOURCE_ID)
        .clientRequestToken(CLIENT_REQUEST_TOKEN)
        .desiredResourceState(desiredState)
        .build();
    val Response = CreateAnalyzerResponse.builder().build();
    doReturn(Response).when(proxy).injectCredentialsAndInvokeV2(any(CreateAnalyzerRequest.class), any());
    val response = invokeHandleRequest(request);
    val actualState = response.getResourceModel();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getMessage()).startsWith("Error creating");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InternalFailure);
  }


  private ProgressEvent<ResourceModel, CallbackContext> invokeHandleRequest(
      ResourceHandlerRequest<ResourceModel> request) {
    return new CreateHandler().handleRequest(proxy, request, new CallbackContext(), logger);
  }
}

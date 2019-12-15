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
import org.mockito.Mock;
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

  @Test
  void testCreateAnalyzer() {
    // The desired state does not mention a name or ARN.  They are created from the
    // client request token and logical resource id.
    val desiredState = ResourceModel.builder()
        .type(TestUtil.ACCOUNT)
        .build();
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
  }

  @Test
  void testLimitExceededServiceException() {
    doThrow(ServiceQuotaExceededException.builder().message("too many analyzers for account").build())
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(), any());
    val desiredState = ResourceModel.builder()
        .arn(ANALYZER_ARN)
        .analyzerName(ANALYZER_NAME)
        .type(TestUtil.ACCOUNT)
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

  @Test
  void testUnknownServiceException() {
    doThrow(new AmazonServiceException("internal failure"))
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(), any());
    val desiredState = ResourceModel.builder()
        .arn(ANALYZER_ARN)
        .analyzerName(ANALYZER_NAME)
        .type(TestUtil.ACCOUNT)
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

  private ProgressEvent<ResourceModel, CallbackContext> invokeHandleRequest(
      ResourceHandlerRequest<ResourceModel> request) {
    return new CreateHandler().handleRequest(proxy, request, new CallbackContext(), logger);
  }
}

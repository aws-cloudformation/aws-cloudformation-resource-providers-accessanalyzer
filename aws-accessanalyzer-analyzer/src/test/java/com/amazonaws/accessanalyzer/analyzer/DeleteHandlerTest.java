package com.amazonaws.accessanalyzer.analyzer;

import static com.amazonaws.accessanalyzer.analyzer.TestUtil.ANALYZER_ARN;
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
import software.amazon.awssdk.services.accessanalyzer.model.DeleteAnalyzerRequest;
import software.amazon.awssdk.services.accessanalyzer.model.DeleteAnalyzerResponse;
import software.amazon.awssdk.services.accessanalyzer.model.ServiceQuotaExceededException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

class DeleteHandlerTest {
  @Mock
  private AmazonWebServicesClientProxy proxy;

  @Mock
  private Logger logger;

  @BeforeEach
  void setup() {
    proxy = mock(AmazonWebServicesClientProxy.class);
    logger = mock(Logger.class);
  }

  // Note, since AccessAnalyzer is OK with deleting a misisng analyzer, this covers
  // that case.
  @Test
  void testSimpleSuccess() {
    val deleteResponse = DeleteAnalyzerResponse.builder().build();
    doReturn(deleteResponse).when(proxy)
        .injectCredentialsAndInvokeV2(any(DeleteAnalyzerRequest.class), any());
    val request = prepareHandlerRequest();
    val response = invokeHandleRequest(request);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    assertThat(response.getCallbackContext()).isNull();
    assertThat(response.getResourceModels()).isNull();
    assertThat(Util.resourceModelIsEmpty(response.getResourceModel()));
    assertThat(response.getMessage()).isNull();
    assertThat(response.getErrorCode()).isNull();
  }

  @Test
  void testNameIsNull() {
    val model = ResourceModel.builder().analyzerName(null).arn(null).build();
    val request = prepareHandlerRequest(model);
    val response = invokeHandleRequest(request);
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
    val model = ResourceModel.builder().analyzerName("").arn(null).build();
    val request = prepareHandlerRequest(model);
    val response = invokeHandleRequest(request);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getCallbackContext()).isNull();
    assertThat(response.getResourceModels()).isNull();
    assertThat(response.getResourceModel()).isEqualTo(model);
    assertThat(response.getMessage()).startsWith("Internal error");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InternalFailure);
  }

  @Test
  void testArnOnly() {
    val model = ResourceModel.builder().analyzerName(null).arn(ANALYZER_ARN).build();
    val request = prepareHandlerRequest(model);
    val response = invokeHandleRequest(request);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    assertThat(response.getCallbackContext()).isNull();
    assertThat(response.getResourceModels()).isNull();
    assertThat(Util.resourceModelIsEmpty(response.getResourceModel()));
    assertThat(response.getMessage()).isNull();
    assertThat(response.getErrorCode()).isNull();
  }

  @Test
  void testLimitExceededServiceException() {
    doThrow(ServiceQuotaExceededException.builder().message("too many analyzers for account").build())
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(), any());
    val request = prepareHandlerRequest();
    val response = invokeHandleRequest(request);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getResourceModel()).isNull();
    assertThat(response.getMessage()).startsWith("too many analyzers for account");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceLimitExceeded);
  }

  @Test
  void testAmazonServiceException() {
    doThrow(new AmazonServiceException("internal failure"))
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(), any());
    val request = prepareHandlerRequest();
    val response = invokeHandleRequest(request);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getResourceModel()).isNull();
    assertThat(response.getMessage()).startsWith("internal failure");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceInternalError);
  }

  private ResourceHandlerRequest<ResourceModel> prepareHandlerRequest() {
    val model = ResourceModel.builder()
        .arn(ANALYZER_ARN)
        .type(TestUtil.ACCOUNT)
        .build();
    return prepareHandlerRequest(model);
  }

  private ResourceHandlerRequest<ResourceModel> prepareHandlerRequest(ResourceModel model) {
    return ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(model)
        .build();
  }

  private ProgressEvent<ResourceModel, CallbackContext> invokeHandleRequest(
      ResourceHandlerRequest<ResourceModel> request) {
    return new DeleteHandler().handleRequest(proxy, request, new CallbackContext(), logger);
  }
}

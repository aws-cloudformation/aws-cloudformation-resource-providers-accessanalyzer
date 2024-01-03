package com.amazonaws.accessanalyzer.analyzer;

import static com.amazonaws.accessanalyzer.analyzer.TestUtil.ANALYZER_ARN;
import static com.amazonaws.accessanalyzer.analyzer.TestUtil.ANALYZER_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.amazonaws.AmazonServiceException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.accessanalyzer.model.AccessDeniedException;
import software.amazon.awssdk.services.accessanalyzer.model.CreateArchiveRuleRequest;
import software.amazon.awssdk.services.accessanalyzer.model.CreateArchiveRuleResponse;
import software.amazon.awssdk.services.accessanalyzer.model.DeleteArchiveRuleRequest;
import software.amazon.awssdk.services.accessanalyzer.model.DeleteArchiveRuleResponse;
import software.amazon.awssdk.services.accessanalyzer.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.accessanalyzer.model.TagResourceRequest;
import software.amazon.awssdk.services.accessanalyzer.model.TagResourceResponse;
import software.amazon.awssdk.services.accessanalyzer.model.UntagResourceRequest;
import software.amazon.awssdk.services.accessanalyzer.model.UntagResourceResponse;
import software.amazon.awssdk.services.accessanalyzer.model.UpdateArchiveRuleRequest;
import software.amazon.awssdk.services.accessanalyzer.model.UpdateArchiveRuleResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

class UpdateHandlerTest {

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
  void testBasicTags() {
    val desiredModel = ResourceModel
        .builder()
        .analyzerName(ANALYZER_NAME)
        .arn(ANALYZER_ARN)
        .type(TestUtil.ACCOUNT)
        .tags(
            ImmutableSet.of(
                Tag.builder().key("c").value("3").build(), // new
                Tag.builder().key("b").value("2").build(), // update
                Tag.builder().key("a").value("1").build()  // ignore
            )
        )
        .build();

    val previousModel = ResourceModel
        .builder()
        .analyzerName(ANALYZER_NAME)
        .arn(ANALYZER_ARN)
        .type(TestUtil.ACCOUNT)
        .tags(
            ImmutableSet.of(
                Tag.builder().key("a").value("1").build(),
                Tag.builder().key("b").value("7").build(),
                Tag.builder().key("z").value("5").build()  // delete
            )
        )
        .build();

    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(desiredModel)
        .previousResourceState(previousModel)
        .build();

    val captor = ArgumentCaptor.forClass(AwsRequest.class);

    doReturn(UntagResourceResponse.builder().build()) // delete z
        .doReturn(TagResourceResponse.builder().build()) // add b, c
        .when(proxy)
        .injectCredentialsAndInvokeV2(captor.capture(), any());

    val response = invokeHandleRequest(request);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    assertThat(response.getCallbackContext()).isNull();
    assertThat(response.getResourceModels()).isNull();
    assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
    assertThat(response.getMessage()).isNull();
    assertThat(response.getErrorCode()).isNull();

    assertThat(captor.getAllValues().size()).isEqualTo(2);
    val untagRequest = (UntagResourceRequest) captor.getAllValues().get(0);
    val tagRequest = (TagResourceRequest) captor.getAllValues().get(1);
    assertThat(untagRequest.tagKeys().equals(Collections.singletonList("z")));
    assertThat(tagRequest.tags().equals(ImmutableMap.of("b", "2", "c", "3")));
  }

  @Test
  void testBasicRules() {
    val desiredModel = ResourceModel
        .builder()
        .analyzerName(ANALYZER_NAME)
        .arn(ANALYZER_ARN)
        .type(TestUtil.ACCOUNT)
        .archiveRules(
            ImmutableList.of(
                ArchiveRule.builder().ruleName("c")
                    .filter(ImmutableList.of(Filter.builder().property("3").build())).build(), // new
                ArchiveRule.builder().ruleName("b")
                    .filter(ImmutableList.of(Filter.builder().property("2").build())).build(), // update
                ArchiveRule.builder().ruleName("a")
                    .filter(ImmutableList.of(Filter.builder().property("1").build())).build() // ignore
            )
        )
        .build();

    val previousModel = ResourceModel
        .builder()
        .analyzerName(ANALYZER_NAME)
        .arn(ANALYZER_ARN)
        .type(TestUtil.ACCOUNT)
        .archiveRules(
            ImmutableList.of(
                ArchiveRule.builder().ruleName("a")
                    .filter(ImmutableList.of(Filter.builder().property("1").build())).build(),
                ArchiveRule.builder().ruleName("b")
                    .filter(ImmutableList.of(Filter.builder().property("7").build())).build(),
                ArchiveRule.builder().ruleName("z")
                    .filter(ImmutableList.of(Filter.builder().property("5").build())).build() // delete
            )
        )
        .build();

    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(desiredModel)
        .previousResourceState(previousModel)
        .build();

    val captor = ArgumentCaptor.forClass(AwsRequest.class);

    doReturn(DeleteArchiveRuleResponse.builder().build()) // delete z
        .doReturn(CreateArchiveRuleResponse.builder().build()) // add c
        .doReturn(UpdateArchiveRuleResponse.builder().build()) // update b
        .when(proxy)
        .injectCredentialsAndInvokeV2(captor.capture(), any());

    val response = invokeHandleRequest(request);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    assertThat(response.getCallbackContext()).isNull();
    assertThat(response.getResourceModels()).isNull();
    assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
    assertThat(response.getMessage()).isNull();
    assertThat(response.getErrorCode()).isNull();

    assertThat(captor.getAllValues().size()).isEqualTo(3);
    val deleteRequest = (DeleteArchiveRuleRequest) captor.getAllValues().get(0);
    val createRequest = (CreateArchiveRuleRequest) captor.getAllValues().get(1);
    val updateRequest = (UpdateArchiveRuleRequest) captor.getAllValues().get(2);
    assertThat(deleteRequest.ruleName().equals("z"));
    assertThat(createRequest.ruleName().equals("c"));
    assertThat(updateRequest.ruleName().equals("b"));
  }

  @Test
  void testAccessDeniedException() {
    doThrow(AccessDeniedException.builder().message("access denied").build())
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(), any());
    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(anOldModel)
        .previousResourceState(aNewModel)
        .build();
    val response = invokeHandleRequest(request);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getResourceModel()).isNull();
    assertThat(response.getMessage()).startsWith("access denied");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.AccessDenied);
  }

  @Test
  void testLimitExceededServiceException() {
    doThrow(ServiceQuotaExceededException.builder().message("too many analyzers for account").build())
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(), any());
    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(anOldModel)
        .previousResourceState(aNewModel)
        .build();
    val response = invokeHandleRequest(request);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getResourceModel()).isNull();
    assertThat(response.getMessage()).startsWith("too many analyzers for account");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceLimitExceeded);
  }

  @Test
  void testUnknownServiceException() {
    doThrow(new AmazonServiceException("internal failure"))
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(), any());
    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(anOldModel)
        .previousResourceState(aNewModel)
        .build();
    val response = invokeHandleRequest(request);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getResourceModel()).isNull();
    assertThat(response.getMessage()).startsWith("internal failure");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceInternalError);
  }

  @Test
  void testAmazonSericeException() {
    AmazonServiceException amazonServiceException = new AmazonServiceException("Unknow Exception");
    amazonServiceException.setStatusCode(400);
    doThrow(amazonServiceException)
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(), any());
    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(anOldModel)
        .previousResourceState(aNewModel)
        .build();
    val response = invokeHandleRequest(request);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getResourceModel()).isNull();
    assertThat(response.getMessage()).startsWith("Unknow Exception");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
  }

  @Test
  void testUntagResourceNotFoundExceptionException() {
    doThrow(SdkServiceException.builder().message("Resource Not Found, Status Code: 404").build())
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(), any());
    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(anOldModel)
        .previousResourceState(aNewModel)
        .build();
    val response = invokeHandleRequest(request);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getMessage()).contains("not found");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
  }

  @Test
  void testUnknownException() {
    doThrow(SdkServiceException.builder().message("Unknown Exception").build())
        .when(proxy)
        .injectCredentialsAndInvokeV2(any(), any());
    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(anOldModel)
        .previousResourceState(aNewModel)
        .build();
    val response = invokeHandleRequest(request);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getMessage()).startsWith("Unknown Exception");
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.ServiceInternalError);
  }

  @Test
  void testPreviousStateNotFound() {
    // empty previous model
    val oldModel = ResourceModel.builder()
        .build();
    val request = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(aNewModel)
        .previousResourceState(oldModel)
        .build();
    val response = invokeHandleRequest(request);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(response.getResourceModel()).isNotNull();
    assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    assertThat(response.getMessage()).startsWith("Internal error");

  }

  private static ResourceModel anOldModel = ResourceModel.builder()
      .analyzerName(ANALYZER_NAME)
      .arn(ANALYZER_ARN)
      .type(TestUtil.ACCOUNT)
      .build();

  private static ResourceModel aNewModel = ResourceModel.builder()
      .analyzerName(ANALYZER_NAME)
      .arn(ANALYZER_ARN)
      .type(TestUtil.ACCOUNT)
      .tags(Collections.singleton(Tag.builder().key("a").value("b").build()))
      .build();

  private ProgressEvent<ResourceModel, CallbackContext> invokeHandleRequest(
      ResourceHandlerRequest<ResourceModel> resourceHandlerRequest) {
    return new UpdateHandler()
        .handleRequest(proxy, resourceHandlerRequest, new CallbackContext(), logger);
  }
}

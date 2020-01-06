package com.amazonaws.accessanalyzer.analyzer;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.util.StringUtils;
import com.google.common.annotations.VisibleForTesting;
import lombok.val;
import software.amazon.awssdk.services.accessanalyzer.AccessAnalyzerClient;
import software.amazon.awssdk.services.accessanalyzer.model.CreateAnalyzerRequest;
import software.amazon.awssdk.services.accessanalyzer.model.ServiceQuotaExceededException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.resource.IdentifierUtils;


public class CreateHandler extends BaseHandler<CallbackContext> {

  private static final int ANALYZER_NAME_MAX_LENGTH = 255;

  @Override
  public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      AmazonWebServicesClientProxy proxy, ResourceHandlerRequest<ResourceModel> request,
      CallbackContext callbackContext, Logger logger) {
    try (val client = ClientBuilder.getClient()) {
      return handleRequestWithClient(client, proxy, request, callbackContext, logger);
    }
  }

  @SuppressWarnings("WeakerAccess")
  @VisibleForTesting
  static ProgressEvent<ResourceModel, CallbackContext> handleRequestWithClient(
      AccessAnalyzerClient client, AmazonWebServicesClientProxy proxy,
      ResourceHandlerRequest<ResourceModel> request,
      @SuppressWarnings("unused") CallbackContext callbackContext, Logger logger) {
    val model = request.getDesiredResourceState();
    String name = model.getAnalyzerName();
    if (StringUtils.isNullOrEmpty(name)) {
      name = IdentifierUtils
          .generateResourceIdentifier(request.getLogicalResourceIdentifier(),
              request.getClientRequestToken(),
              ANALYZER_NAME_MAX_LENGTH);
      model.setAnalyzerName(name);
      logger.log("No name in request.  Invented name: " + name);
    }
    val rules = Util.map(Util.resourceRules(model), Util::inlineArchiveRuleFromArchiveRule);
    val createRequest = CreateAnalyzerRequest.builder()
        .analyzerName(name)
        .archiveRules(rules)
        .tags(Util.tagsToMap(Util.resourceTags(model)))
        .type(model.getType())
        .build();
    try {
      val result = proxy.injectCredentialsAndInvokeV2(createRequest, client::createAnalyzer);
      val arn = result.arn();
      if (arn == null) {
        logger.log(String.format("ERROR: Impossible.  Null ARN from create: %s", name));
        return ProgressEvent
            .failed(request.getDesiredResourceState(), null, HandlerErrorCode.InternalFailure,
                String.format("Error creating %s", name));
      }
      model.setArn(result.arn());
      logger.log(String.format("%s [%s] Created Successfully", ResourceModel.TYPE_NAME, name));
      return ProgressEvent.defaultSuccessHandler(model);
    } catch (ServiceQuotaExceededException ex) {
      logger.log(
          String.format("%s [%s] Too many analyzers", ResourceModel.TYPE_NAME, name));
      return ProgressEvent.defaultFailureHandler(ex, HandlerErrorCode.ServiceLimitExceeded);
    } catch (AmazonServiceException ex) {
      if (ex.getStatusCode() == Util.SERVICE_VALIDATION_STATUS_CODE) {
        logger.log(String.format("%s [%s] Create Failed due to a service validation error",
            ResourceModel.TYPE_NAME, name));
        return ProgressEvent.defaultFailureHandler(ex, HandlerErrorCode.InvalidRequest);
      }
      logger.log(String.format("%s [%s] Created Failed", ResourceModel.TYPE_NAME, name));
      return ProgressEvent.defaultFailureHandler(ex, HandlerErrorCode.ServiceInternalError);
    } catch (Exception ex) {
      logger.log(String.format("%s [%s] Created Failed", ResourceModel.TYPE_NAME, name));
      return ProgressEvent.defaultFailureHandler(ex, HandlerErrorCode.ServiceInternalError);
    }
  }
}

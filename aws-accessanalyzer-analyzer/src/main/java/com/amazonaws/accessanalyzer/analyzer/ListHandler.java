package com.amazonaws.accessanalyzer.analyzer;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.Optional;
import lombok.val;
import software.amazon.awssdk.services.accessanalyzer.AccessAnalyzerClient;
import software.amazon.awssdk.services.accessanalyzer.model.ListAnalyzersRequest;
import software.amazon.awssdk.services.accessanalyzer.model.ListAnalyzersResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ListHandler extends BaseHandler<CallbackContext> {

  private static final int MAX_RESULTS = 100;

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
      AccessAnalyzerClient client,
      AmazonWebServicesClientProxy proxy, ResourceHandlerRequest<ResourceModel> request,
      @SuppressWarnings("unused") CallbackContext callbackContext, Logger logger) {
    val listRequest = ListAnalyzersRequest.builder()
        .maxResults(MAX_RESULTS)
        .nextToken(request.getNextToken())
        .build();
    ListAnalyzersResponse result;
    try {
      result = proxy.injectCredentialsAndInvokeV2(listRequest, client::listAnalyzers);
    } catch (Exception ex) {
      logger.log(String.format("%s List failed", ResourceModel.TYPE_NAME));
      return ProgressEvent.defaultFailureHandler(ex, HandlerErrorCode.ServiceInternalError);
    }
    // TODO: Handle more exceptions

    val analyzers = Optional.ofNullable(result.analyzers()).orElse(Collections.emptyList());
    val models = Util.map(analyzers, Util::analyzerSummaryToModel);
    return ProgressEvent.<ResourceModel, CallbackContext>builder()
        .resourceModels(models)
        .nextToken(result.nextToken())
        .status(OperationStatus.SUCCESS)
        .build();
  }
}

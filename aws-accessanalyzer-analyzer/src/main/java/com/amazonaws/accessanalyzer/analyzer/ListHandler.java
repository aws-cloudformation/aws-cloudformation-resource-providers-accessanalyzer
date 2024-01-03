package com.amazonaws.accessanalyzer.analyzer;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.List;
import lombok.val;
import software.amazon.awssdk.services.accessanalyzer.AccessAnalyzerClient;
import software.amazon.awssdk.services.accessanalyzer.model.AccessDeniedException;
import software.amazon.awssdk.services.accessanalyzer.model.AnalyzerSummary;
import software.amazon.awssdk.services.accessanalyzer.model.ListAnalyzersRequest;
import software.amazon.awssdk.services.accessanalyzer.model.ListAnalyzersResponse;
import software.amazon.awssdk.services.accessanalyzer.AccessAnalyzerClient;
import software.amazon.awssdk.services.accessanalyzer.model.Type;
import software.amazon.awssdk.services.accessanalyzer.paginators.ListAnalyzersIterable;
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
    try {
      val result = proxy
          .injectCredentialsAndInvokeIterableV2(listRequest, client::listAnalyzersPaginator);
      val analyzers = result.stream().flatMap(v -> v.analyzers().stream())
          .collect(Collectors.toList());
      val models = Util.map(analyzers, Util::analyzerSummaryToModel);
      return ProgressEvent.<ResourceModel, CallbackContext>builder()
          .resourceModels(models)
          .status(OperationStatus.SUCCESS)
          .build();
    } catch (AccessDeniedException ex) {
      logger.log(String.format("%s List denied", ResourceModel.TYPE_NAME));
      return ProgressEvent.defaultFailureHandler(ex, HandlerErrorCode.AccessDenied);
    } catch (Exception ex) {
      logger.log(String.format("%s List failed", ResourceModel.TYPE_NAME));
      return ProgressEvent.defaultFailureHandler(ex, HandlerErrorCode.ServiceInternalError);
    }
    // TODO: Handle more exceptions
  }
}

package com.amazonaws.accessanalyzer.analyzer;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Optional;
import lombok.val;
import software.amazon.awssdk.services.accessanalyzer.AccessAnalyzerClient;
import software.amazon.awssdk.services.accessanalyzer.model.GetAnalyzerRequest;
import software.amazon.awssdk.services.accessanalyzer.model.GetAnalyzerResponse;
import software.amazon.awssdk.services.accessanalyzer.model.ListArchiveRulesRequest;
import software.amazon.awssdk.services.accessanalyzer.model.ListArchiveRulesResponse;
import software.amazon.awssdk.services.accessanalyzer.model.ResourceNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ReadHandler extends BaseHandler<CallbackContext> {

  @VisibleForTesting
  static String NO_ANALYZER_MESSAGE_PREFIX = "No analyzer named ";

  @Override
  public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      AmazonWebServicesClientProxy proxy, ResourceHandlerRequest<ResourceModel> request,
      CallbackContext callbackContext, Logger logger) {
    try (val client = ClientBuilder.getClient()) {
      return handleRequestWithClient(client, proxy, request, callbackContext, logger);
    }
  }

  @SuppressWarnings({"DuplicatedCode", "WeakerAccess"})
  @VisibleForTesting
  static ProgressEvent<ResourceModel, CallbackContext> handleRequestWithClient(
      AccessAnalyzerClient client, AmazonWebServicesClientProxy proxy,
      ResourceHandlerRequest<ResourceModel> request,
      @SuppressWarnings("unused") CallbackContext callbackContext, Logger logger) {
    val model = request.getDesiredResourceState();
    val arn = model.getArn();
    if (arn == null) {
      logger.log("Impossible: Null arn in current state of analyzer");
      return ProgressEvent
          .failed(request.getDesiredResourceState(), null, HandlerErrorCode.InternalFailure,
              "Internal error");
    }
    // CFN is inconsistent about returning the AnalyzerName used in the CREATE call
    val name = Optional.ofNullable(model.getAnalyzerName()).orElse(Util.arnToAnalyzerName(arn));
    val getAnalyzerRequest = GetAnalyzerRequest.builder().analyzerName(name).build();
    GetAnalyzerResponse getAnalyzerResponse;
    ListArchiveRulesRequest listArchiveRulesRequest = ListArchiveRulesRequest.builder()
        .analyzerName(name).build();
    val archiveRules = new ArrayList<ArchiveRule>();
    try {
      ListArchiveRulesResponse listArchiveRulesResponse;
      getAnalyzerResponse = proxy
          .injectCredentialsAndInvokeV2(getAnalyzerRequest, client::getAnalyzer);
      do {
        listArchiveRulesResponse = proxy
            .injectCredentialsAndInvokeV2(listArchiveRulesRequest, client::listArchiveRules);
        archiveRules.addAll(
            Util.map(listArchiveRulesResponse.archiveRules(), Util::archiveRuleFromSummary));
        listArchiveRulesRequest = ListArchiveRulesRequest.builder().analyzerName(name)
            .nextToken(listArchiveRulesResponse.nextToken()).build();
      } while (listArchiveRulesResponse.nextToken() != null);
    } catch (ResourceNotFoundException ex) {
      val msg = NO_ANALYZER_MESSAGE_PREFIX + name;
      logger.log(msg);
      return ProgressEvent.failed(model, null, HandlerErrorCode.NotFound, msg);
    } catch (Exception ex) {
      logger.log(String.format("%s [%s] Get analyzer failed", ResourceModel.TYPE_NAME, name));
      return ProgressEvent.defaultFailureHandler(ex, HandlerErrorCode.ServiceInternalError);
    }
    // TODO: Handle more errors
    val summary = getAnalyzerResponse.analyzer(); // TODO: Compare summary with model
    val resultModel = ResourceModel
        .builder()
        .analyzerName(name)
        .type(summary.typeAsString())
        .arn(summary.arn())
        .tags(Util.mapToTags(summary.tags()))
        .archiveRules(archiveRules)
        .build();
    return ProgressEvent.defaultSuccessHandler(resultModel);
  }
}

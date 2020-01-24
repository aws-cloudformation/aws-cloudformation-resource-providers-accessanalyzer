package com.amazonaws.accessanalyzer.analyzer;

import com.amazonaws.AmazonServiceException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import lombok.val;
import software.amazon.awssdk.services.accessanalyzer.AccessAnalyzerClient;
import software.amazon.awssdk.services.accessanalyzer.model.CreateArchiveRuleRequest;
import software.amazon.awssdk.services.accessanalyzer.model.DeleteArchiveRuleRequest;
import software.amazon.awssdk.services.accessanalyzer.model.ResourceNotFoundException;
import software.amazon.awssdk.services.accessanalyzer.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.accessanalyzer.model.TagResourceRequest;
import software.amazon.awssdk.services.accessanalyzer.model.UntagResourceRequest;
import software.amazon.awssdk.services.accessanalyzer.model.UpdateArchiveRuleRequest;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class UpdateHandler extends BaseHandler<CallbackContext> {

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
    val oldModel = request.getPreviousResourceState();
    val newModel = request.getDesiredResourceState();

    val arn = oldModel.getArn();
    if (arn == null) {
      logger.log("Impossible: Null arn in current state of analyzer");
      return ProgressEvent
          .failed(request.getDesiredResourceState(), null, HandlerErrorCode.InternalFailure,
              "Internal error");
    }
    newModel.setArn(arn);

    // CFN only returns AnalyzerName used in the CREATE call
    // if it was part of the user template.
    // https://sage.amazon.com/questions/783984
    val name = Optional.ofNullable(oldModel.getAnalyzerName())
        .orElse(Util.arnToAnalyzerName(arn));

    // AnalyzerName can't be changed, but if the user doesn't supply it use the existing name
    if (newModel.getAnalyzerName() == null) {
      logger.log("Setting new analyzer name to " + name);
      newModel.setAnalyzerName(name);
    }

    if (!name.equals(newModel.getAnalyzerName())) {
      return ProgressEvent
          .failed(request.getDesiredResourceState(), null, HandlerErrorCode.NotUpdatable, String
              .format("%s [%s] cannot be modified as AnalyzerName was changed",
                  ResourceModel.TYPE_NAME, name));
    }

    if (!oldModel.getType().equals(newModel.getType())) {
      return ProgressEvent
          .failed(request.getDesiredResourceState(), null, HandlerErrorCode.NotUpdatable, String
              .format("%s [%s] cannot be modified as Type was changed", ResourceModel.TYPE_NAME,
                  name));
    }

    // Tags
    val oldTags = Util.resourceTags(oldModel);
    val oldTagKeys = Util.setMap(oldTags, Tag::getKey);
    val newTags = Util.resourceTags(newModel);
    val newTagKeys = Util.setMap(newTags, Tag::getKey);
    val tagKeysToRemove = new ArrayList<String>(Sets.difference(oldTagKeys, newTagKeys));
    tagKeysToRemove.sort(Comparator.naturalOrder()); // Stable order for testing
    val tagsToAdd = Util.filter(newTags, newTag -> oldTags.stream().noneMatch(newTag::equals));
    tagsToAdd.sort(Comparator.comparing(Tag::getKey)); // Stable order for testing

    // Rules
    val oldRules = Util.resourceRules(oldModel);
    val oldRuleNames = Util.setMap(oldRules, Util::ruleName);
    val newRules = Util.resourceRules(newModel);
    val newRuleNames = Util.setMap(newRules, Util::ruleName);
    val ruleNamesToRemove = new ArrayList<String>(Sets.difference(oldRuleNames, newRuleNames));
    ruleNamesToRemove.sort(Comparator.naturalOrder()); // Stable order for testing
    val rulesToAdd = new ArrayList<ArchiveRule>();
    val rulesToUpdate = new ArrayList<ArchiveRule>();
    for (val newRule : Util.resourceRules(newModel)) {
      if (oldRules.stream().noneMatch(newRule::equals)) {
        if (oldRuleNames.contains(Util.ruleName(newRule))) {
          rulesToUpdate.add(newRule);
        } else {
          rulesToAdd.add(newRule);
        }
      }
    }
    rulesToAdd.sort(Comparator.comparing(Util::ruleName)); // Stable order for testing
    rulesToUpdate.sort(Comparator.comparing(Util::ruleName)); // Stable order for testing

    try {
      if (!tagKeysToRemove.isEmpty()) {
        logger
            .log(String
                .format("Deleting %d tags for analyzer %s", tagKeysToRemove.size(), name));
        val deleteTagsRequest = UntagResourceRequest.builder().resourceArn(arn)
            .tagKeys(tagKeysToRemove).build();
        proxy.injectCredentialsAndInvokeV2(deleteTagsRequest, client::untagResource);
      }
      if (!tagsToAdd.isEmpty()) {
        logger.log(String.format("Adding %d tags for analyzer %s", tagsToAdd.size(), name));
        val addTagsRequest = TagResourceRequest.builder().resourceArn(arn)
            .tags(Util.tagsToMap(tagsToAdd)).build();
        proxy.injectCredentialsAndInvokeV2(addTagsRequest, client::tagResource);
      }
      ruleNamesToRemove.forEach(ruleName -> {
        logger.log(String.format("Deleting archive rule %s for analyzer %s", ruleName, name));
        val deleteRuleRequest = DeleteArchiveRuleRequest.builder().analyzerName(name)
            .ruleName(ruleName).build();
        proxy.injectCredentialsAndInvokeV2(deleteRuleRequest, client::deleteArchiveRule);
      });
      rulesToAdd.forEach(rule -> {
        logger.log(
            String.format("Adding archive rule %s for analyzer %s", Util.ruleName(rule), name));
        val inline = Util.inlineArchiveRuleFromArchiveRule(rule);
        val createRuleRequest = CreateArchiveRuleRequest.builder().analyzerName(name)
            .ruleName(inline.ruleName()).filter(inline.filter()).build();
        proxy.injectCredentialsAndInvokeV2(createRuleRequest, client::createArchiveRule);
      });
      rulesToUpdate.forEach(rule -> {
        logger.log(
            String.format("Updating archive rule %s for analyzer %s", Util.ruleName(rule), name));
        val inline = Util.inlineArchiveRuleFromArchiveRule(rule);
        val updateRuleRequest = UpdateArchiveRuleRequest.builder().analyzerName(name)
            .ruleName(inline.ruleName()).filter(inline.filter()).build();
        proxy.injectCredentialsAndInvokeV2(updateRuleRequest, client::updateArchiveRule);
      });
      logger.log(String.format("%s [%s] Updated Successfully", ResourceModel.TYPE_NAME, name));
      return ProgressEvent.defaultSuccessHandler(newModel);
    } catch (ResourceNotFoundException ex) {
      logger.log(
          String.format("%s [%s] not found and must be created", ResourceModel.TYPE_NAME, name));
      return ProgressEvent
          .failed(request.getDesiredResourceState(), null, HandlerErrorCode.NotFound, String
              .format("%s [%s] not found and must be created", ResourceModel.TYPE_NAME, name));
    } catch (ServiceQuotaExceededException ex) {
      logger.log(
          String.format("%s [%s] too many tags or archive rules", ResourceModel.TYPE_NAME, name));
      return ProgressEvent.defaultFailureHandler(ex, HandlerErrorCode.ServiceLimitExceeded);
    } catch (AmazonServiceException ex) {
      if (ex.getStatusCode() == Util.SERVICE_VALIDATION_STATUS_CODE) {
        logger.log(String.format("%s [%s] Update Failed due to a service validation error",
            ResourceModel.TYPE_NAME, name));
        return ProgressEvent.defaultFailureHandler(ex, HandlerErrorCode.InvalidRequest);
      }
      logger.log(String.format("%s [%s] Updated Failed", ResourceModel.TYPE_NAME, name));
      return ProgressEvent.defaultFailureHandler(ex, HandlerErrorCode.ServiceInternalError);
    } catch (Exception ex) {
      logger.log(String.format("%s [%s] Updated Failed", ResourceModel.TYPE_NAME, name));
      return ProgressEvent.defaultFailureHandler(ex, HandlerErrorCode.ServiceInternalError);
    }
    // TODO: Handle more exceptions
  }
}

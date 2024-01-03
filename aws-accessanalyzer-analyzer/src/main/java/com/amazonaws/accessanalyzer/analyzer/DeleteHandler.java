package com.amazonaws.accessanalyzer.analyzer;

import static com.amazonaws.accessanalyzer.analyzer.ResourceModel.TYPE_NAME;

import java.util.Optional;
import lombok.val;
import software.amazon.awssdk.services.accessanalyzer.AccessAnalyzerClient;
import software.amazon.awssdk.services.accessanalyzer.model.AccessAnalyzerException;
import software.amazon.awssdk.services.accessanalyzer.model.AccessDeniedException;
import software.amazon.awssdk.services.accessanalyzer.model.ConflictException;
import software.amazon.awssdk.services.accessanalyzer.model.DeleteAnalyzerRequest;
import software.amazon.awssdk.services.accessanalyzer.model.InternalServerException;
import software.amazon.awssdk.services.accessanalyzer.model.ResourceNotFoundException;
import software.amazon.awssdk.services.accessanalyzer.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.accessanalyzer.model.ThrottlingException;
import software.amazon.awssdk.services.accessanalyzer.model.ValidationException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandler extends BaseHandler<CallbackContext> {

  @Override
  public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      AmazonWebServicesClientProxy proxy, ResourceHandlerRequest<ResourceModel> request,
      CallbackContext callbackContext, Logger logger) {
    try (val client = ClientBuilder.getClient()) {
      return handleRequestWithClient(client, proxy, request, callbackContext, logger);
    }
  }

  @SuppressWarnings("DuplicatedCode") // TODO: Abstract the ARN/Name logic
  static ProgressEvent<ResourceModel, CallbackContext> handleRequestWithClient(
      AccessAnalyzerClient client, AmazonWebServicesClientProxy proxy,
      ResourceHandlerRequest<ResourceModel> request,
      CallbackContext callbackContext, Logger logger) {
    val model = request.getDesiredResourceState();
    // Delete handler must not a return a resource model
    // https://code.amazon.com/packages/AWSCloudFormationRPDK/blobs/1e4bf79dcedfaee7072d5431d003a042f312823d/--/src/rpdk/core/contract/suite/resource/handler_delete.py#L39
    final ResourceModel returnedModel = null;
    val arn = model.getArn();
    if (arn == null) {
      logger.log("Impossible: Null arn in current state of analyzer");
      return ProgressEvent
          .failed(returnedModel,
              null,
              HandlerErrorCode.InternalFailure,
              "Internal error");
    }
    // CFN is inconsistent about returning the AnalyzerName used in the CREATE call
    val name = Optional.ofNullable(model.getAnalyzerName()).orElse(Util.arnToAnalyzerName(arn));
    try {
      val deleteRequest = DeleteAnalyzerRequest.builder().analyzerName(name).build();
      proxy.injectCredentialsAndInvokeV2(deleteRequest, client::deleteAnalyzer);
      logger.log(String.format("%s [%s] Deleted Successfully", ResourceModel.TYPE_NAME, name));
      // Delete handler must not a return a resource model
      // https://code.amazon.com/packages/AWSCloudFormationRPDK/blobs/1e4bf79dcedfaee7072d5431d003a042f312823d/--/src/rpdk/core/contract/suite/resource/handler_delete.py#L39
      return ProgressEvent.defaultSuccessHandler(returnedModel);
    } catch (AccessDeniedException ex) {
      logError(logger, name, ex);
      return ProgressEvent.failed(returnedModel, null, HandlerErrorCode.AccessDenied, "Access denied");
    } catch (ConflictException | ValidationException ex) {
      logError(logger, name, ex);
      return ProgressEvent.failed(returnedModel, null, HandlerErrorCode.InvalidRequest, "Invalid request");
    } catch (InternalServerException ex) {
      logError(logger, name, ex);
      return ProgressEvent
          .failed(returnedModel, null, HandlerErrorCode.ServiceInternalError, "Internal error");
    } catch (ResourceNotFoundException ex) {
      logError(logger, name, ex);
      return ProgressEvent.failed(returnedModel, null, HandlerErrorCode.NotFound,
          String.format("No analyzer named %s", name));
    } catch (ServiceQuotaExceededException ex) {
      logError(logger, name, ex);
      logger.log(
          "Impossible: got a limit-exceeded exception when deleting an analyzer: " + ex.toString());
      return ProgressEvent.defaultFailureHandler(ex, HandlerErrorCode.ServiceLimitExceeded);
    } catch (ThrottlingException ex) {
      logError(logger, name, ex);
      return ProgressEvent.failed(returnedModel, null, HandlerErrorCode.Throttling, "Throttled");
    } catch (AccessAnalyzerException ex) {
      logError(logger, name, ex, "Impossible: unhandled AccessAnalyzerException subtype");
      return ProgressEvent.defaultFailureHandler(ex, HandlerErrorCode.ServiceInternalError);
    } catch (RuntimeException ex) {
      logError(logger, name, ex, "Unhandled RuntimeException");
      return ProgressEvent.defaultFailureHandler(ex, HandlerErrorCode.ServiceInternalError);
    }
  }

  private static void logError(Logger logger, String analyzerName, Exception exn) {
    logger.log(String.format("Exception while deleting %s named %s: %s", TYPE_NAME, analyzerName,
        exn.toString()));
  }

  private static void logError(Logger logger, String analyzerName, Exception exn, String message) {
    logger.log(String
        .format("Exception while deleting %s named %s: %s: %s", TYPE_NAME, analyzerName,
            exn.toString(), message));
  }
}

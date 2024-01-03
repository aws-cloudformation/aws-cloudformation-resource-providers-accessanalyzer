package com.amazonaws.accessanalyzer.analyzer;

import java.time.Duration;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.services.accessanalyzer.AccessAnalyzerClient;
import software.amazon.cloudformation.LambdaWrapper;

class ClientBuilder {

  // The CFN handler timeout is 60s:
  //   - https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-type-walkthrough.html
  //   - https://sage.amazon.com/questions/767822
  // AccessAnalyzer regularly has long latencies (20s or higher) when used for the first time:
  //   - https://code.amazon.com/reviews/CR-16244839/revisions/1#/comments
  // We'll use 3 19s timeouts, which should handle any initial-use latency and be within the CFN limit
  static AccessAnalyzerClient getClient() {
    return AccessAnalyzerClient
        .builder()
        .overrideConfiguration(
            ClientOverrideConfiguration
                .builder()
                .apiCallAttemptTimeout(Duration.ofSeconds(19))
                .apiCallTimeout(Duration.ofSeconds(59))
                .retryPolicy(RetryPolicy.builder().numRetries(3).build())
                .build())
        // It is safe to close this client, which will not close the static http client
        //   - https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/client-configuration-http.html
        .httpClient(LambdaWrapper.HTTP_CLIENT)
        .build();
  }
}

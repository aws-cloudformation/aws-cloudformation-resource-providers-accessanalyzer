package com.amazonaws.accessanalyzer.analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UtilTest {

  @Test
  void testArnToName() {
    assertThat(Util.arnToAnalyzerName(
        "arn:aws:access-analyzer:us-west-2:111111111111:analyzer/CanaryAnalyzerTest"))
        .isEqualTo("CanaryAnalyzerTest");
  }
}

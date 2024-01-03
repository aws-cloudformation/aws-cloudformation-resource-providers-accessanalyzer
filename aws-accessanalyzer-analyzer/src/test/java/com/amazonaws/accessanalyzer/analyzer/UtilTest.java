package com.amazonaws.accessanalyzer.analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.stream.Stream;
import java.util.Optional;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.services.accessanalyzer.model.ArchiveRuleSummary;
import software.amazon.awssdk.services.accessanalyzer.model.Criterion;
import software.amazon.awssdk.utils.ImmutableMap;

class UtilTest {

  @Test
  void testArnToName() {
    assertThat(Util.arnToAnalyzerName(
        "arn:aws:access-analyzer:us-west-2:111111111111:analyzer/CanaryAnalyzerTest"))
        .isEqualTo("CanaryAnalyzerTest");
  }

  static Stream<Arguments> testArchiveRuleFromSummary() {
    return Stream.of(
        Arguments.of(
            ArchiveRuleSummary.builder()
                .ruleName("TestRule")
                .filter(ImmutableMap.of("property", Criterion.builder().exists(true).build()))
                .build(),
            Filter.builder().property("property").exists(true).build()
        ),
        Arguments.of(
            ArchiveRuleSummary.builder()
                .ruleName("TestRule")
                .filter(ImmutableMap.of("property", Criterion.builder().contains("1111").build()))
                .build(),
            Filter.builder().property("property").contains(Collections.singletonList("1111")).build()
        ),
        Arguments.of(
            ArchiveRuleSummary.builder()
                .ruleName("TestRule")
                .filter(ImmutableMap.of("property", Criterion.builder().eq("1111").build()))
                .build(),
            Filter.builder().property("property").eq(Collections.singletonList("1111")).build()
        ),
        Arguments.of(
            ArchiveRuleSummary.builder()
                .ruleName("TestRule")
                .filter(ImmutableMap.of("property", Criterion.builder().neq("1111").build()))
                .build(),
            Filter.builder().property("property").neq(Collections.singletonList("1111")).build()
        )
    );
  }

  @ParameterizedTest
  @MethodSource
  void testArchiveRuleFromSummary(ArchiveRuleSummary archiveRuleSummary, Filter expectedFilter) {
    val archiveRule = Util.archiveRuleFromSummary(archiveRuleSummary);
    assertThat(archiveRule.getRuleName().equals("TestRule"));
    assertThat(archiveRule.getFilter().get(0).equals(expectedFilter));
  }

    @Test
    void testGetUnusedAccessAgeFromResourceModel() {
        ResourceModel resourceModel = ResourceModel.builder()
            .type(TestUtil.ACCOUNT_UNUSED_ACCESS)
            .analyzerConfiguration(AnalyzerConfiguration.builder()
                .unusedAccessConfiguration(UnusedAccessConfiguration.builder()
                    .unusedAccessAge(60)
                    .build())
                .build())
            .build();
        Optional<Integer> accessAge = Util.getUnusedAccessAgeFromResourceModel(resourceModel);
        assertThat(!accessAge.isPresent());
        assertThat(Util.getUnusedAccessAgeFromResourceModel(resourceModel).get() == 60);
    }

    @Test
    void testGetUnusedAccessAgeFromResourceModel_AnalyerConfigurationIsNull() {
        ResourceModel resourceModel = ResourceModel.builder()
            .type(TestUtil.ACCOUNT_UNUSED_ACCESS)
            .build();
        Optional<Integer> accessAge = Util.getUnusedAccessAgeFromResourceModel(resourceModel);
        assertThat(!accessAge.isPresent());
    }

    @Test
    void testGetUnusedAccessAgeFromResourceModel_AccessAgeIsNull() {
        ResourceModel resourceModel = ResourceModel.builder()
            .type(TestUtil.ACCOUNT_UNUSED_ACCESS)
            .analyzerConfiguration(AnalyzerConfiguration.builder()
                .unusedAccessConfiguration(UnusedAccessConfiguration.builder()
                    .build())
                .build())
            .build();
        Optional<Integer> accessAge = Util.getUnusedAccessAgeFromResourceModel(resourceModel);
        assertThat(!accessAge.isPresent());
    }
}

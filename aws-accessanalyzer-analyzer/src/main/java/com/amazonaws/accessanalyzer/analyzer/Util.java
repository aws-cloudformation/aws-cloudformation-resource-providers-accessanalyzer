package com.amazonaws.accessanalyzer.analyzer;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.val;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.services.accessanalyzer.model.AnalyzerSummary;
import software.amazon.awssdk.services.accessanalyzer.model.ArchiveRuleSummary;
import software.amazon.awssdk.services.accessanalyzer.model.Criterion;
import software.amazon.awssdk.services.accessanalyzer.model.InlineArchiveRule;
import software.amazon.awssdk.utils.StringUtils;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;

class Util {

  static final int SERVICE_VALIDATION_STATUS_CODE = 400;

  static <A, B> List<B> map(Collection<A> xs, Function<A, B> f) {
    return Optional.ofNullable(xs).orElse(Collections.emptyList()).stream().map(f)
        .collect(Collectors.toList());
  }

  static <A> List<A> concat(Collection<A> xs, Collection<A> ys) {
    return Stream.concat(
        Optional.ofNullable(xs).orElse(Collections.emptyList()).stream(),
        Optional.ofNullable(ys).orElse(Collections.emptyList()).stream()
    ).collect(Collectors.toList());
  }

  static <A, B> Set<B> setMap(Collection<A> xs, Function<A, B> f) {
    return Optional.ofNullable(xs).orElse(Collections.emptySet()).stream().map(f)
        .collect(Collectors.toSet());
  }

  static <A> List<A> filter(Collection<A> xs, Predicate<A> p) {
    return Optional.ofNullable(xs).orElse(Collections.emptyList()).stream().filter(p)
        .collect(Collectors.toList());
  }

  static Set<Tag> resourceTags(ResourceModel m) {
    return Optional.ofNullable(m.getTags()).orElse(Collections.emptySet());
  }

  static List<ArchiveRule> resourceRules(ResourceModel m) {
    return Optional.ofNullable(m.getArchiveRules()).orElse(Collections.emptyList());
  }

  static String arnToAnalyzerName(String arnStr) {
    try {
      val arn = Arn.fromString(arnStr);
      return arn.resource().resource();
    } catch (IllegalArgumentException ex) {
      throw new CfnInternalFailureException(ex);
    }
  }

  static ResourceModel analyzerSummaryToModel(AnalyzerSummary summary) {
    return ResourceModel
        .builder()
        .analyzerName(summary.name())
        .type(summary.typeAsString())
        .arn(summary.arn())
        .tags(mapToTags(Optional.ofNullable(summary.tags()).orElse(Collections.emptyMap())))
        .build();
  }

  static String ruleName(ArchiveRule rule) {
    return rule.getRuleName();
  }

  static Boolean isNullOrEmpty(String s) {
    return Objects.nonNull(s) && StringUtils.isEmpty(s);
  }

  static Boolean resourceModelIsEmpty(ResourceModel model) {
    return
        isNullOrEmpty(model.getAnalyzerName()) &&
            isNullOrEmpty(model.getType()) &&
            isNullOrEmpty(model.getArn());
  }

  static InlineArchiveRule inlineArchiveRuleFromArchiveRule(ArchiveRule rule) {
    return InlineArchiveRule.builder()
        .ruleName(ruleName(rule))
        .filter(
            rule.getFilter().stream().collect(Collectors.toMap(
                Filter::getProperty,
                f -> Criterion.builder()
                    .eq(f.getEq())
                    .neq(f.getNeq())
                    .exists(f.getExists())
                    .contains(f.getContains())
                    .build()
            ))
        )
        .build();
  }

  static ArchiveRule archiveRuleFromSummary(ArchiveRuleSummary summary) {
    return ArchiveRule.builder()
        .ruleName(summary.ruleName())
        .filter(
            summary
                .filter()
                .entrySet()
                .stream()
                .map(e ->
                    Filter.builder()
                        .property(e.getKey())
                        .contains(e.getValue().contains())
                        .eq(e.getValue().eq())
                        .neq(e.getValue().neq())
                        .exists(e.getValue().exists())
                        .build())
                .collect(Collectors.toList())
        )
        .build();
  }

  static Map<String, String> tagsToMap(Collection<Tag> tags) {
    return Optional.ofNullable(tags).orElse(Collections.emptyList()).stream()
        .collect(Collectors.toMap(Tag::getKey, Tag::getValue));
  }

  static Set<Tag> mapToTags(Map<String, String> m) {
    return Optional.ofNullable(m).orElse(Collections.emptyMap()).entrySet().stream()
        .map(e -> new Tag(e.getKey(), e.getValue()))
        .collect(Collectors.toSet());
  }
}

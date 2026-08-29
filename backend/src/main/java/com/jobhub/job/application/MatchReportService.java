package com.jobhub.job.application;

import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.error.ResourceNotFoundException;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.job.domain.*;
import com.jobhub.job.infrastructure.JobMapper;
import com.jobhub.job.infrastructure.MatchReportMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可解释岗位匹配报告（PRD 9.1）。基于差距清单做规则计算：
 * 必须要求与加分要求分别汇总与加权计分（MUST=3、BONUS=1），缺少资料不计入分母；
 * 每次生成保存权重、规则版本、生成时间与完整数据快照；输入变化后读取时标记 stale=true。
 */
@Service
public class MatchReportService {
	public static final String RULE_VERSION = "MATCH_RULE_V1";
	private static final int WEIGHT_MUST = 3;
	private static final int WEIGHT_BONUS = 1;
	private static final double VALUE_SATISFIED = 1.0;
	private static final double VALUE_SELF_REPORTED = 0.5;
	private static final double VALUE_NOT_MET = 0.0;

	private static final ObjectMapper JSON = new ObjectMapper();

	private final JobMapper jobMapper;
	private final MatchReportMapper matchReportMapper;
	private final GapListService gapListService;
	private final IdGenerator ids;
	private final UtcTime time;

	public MatchReportService(JobMapper jobMapper, MatchReportMapper matchReportMapper,
			GapListService gapListService, IdGenerator ids, UtcTime time) {
		this.jobMapper = jobMapper;
		this.matchReportMapper = matchReportMapper;
		this.gapListService = gapListService;
		this.ids = ids;
		this.time = time;
	}

	@Transactional
	public MatchReportView generate(String jobId) {
		Job job = jobMapper.selectById(jobId);
		VersionCheck.requireFound(job, "Job", jobId);
		List<GapItem> items = gapListService.getGapList(jobId);
		MatchReportContent content = build(items);
		String fingerprint = fingerprint(items);
		String now = time.now();
		MatchReport report = new MatchReport();
		report.setId(ids.newId());
		report.setJobId(jobId);
		report.setRuleVersion(RULE_VERSION);
		report.setWeightsJson(weightsJson());
		report.setReportJson(toJson(content));
		report.setInputFingerprint(fingerprint);
		report.setGeneratedAt(now);
		matchReportMapper.insert(report);
		return view(matchReportMapper.selectById(report.getId()), false);
	}

	public MatchReportView latest(String jobId) {
		Job job = jobMapper.selectById(jobId);
		VersionCheck.requireFound(job, "Job", jobId);
		MatchReport report = matchReportMapper.selectLatestByJob(jobId);
		if (report == null) {
			throw new ResourceNotFoundException("MatchReport", jobId);
		}
		String current = fingerprint(gapListService.getGapList(jobId));
		boolean stale = !current.equals(report.getInputFingerprint());
		return view(report, stale);
	}

	private MatchReportContent build(List<GapItem> items) {
		List<MatchReportContent.Item> requirementItems = new ArrayList<>();
		for (GapItem item : items) {
			requirementItems.add(new MatchReportContent.Item(
				item.requirement().getId(),
				item.requirement().getNormalizedName(),
				item.requirement().getRawText(),
				item.requirement().getType(),
				item.status(),
				reasonsFor(item)));
		}

		MatchReportContent.Summary must = summarize(items, RequirementType.MUST);
		MatchReportContent.Summary bonus = summarize(items, RequirementType.BONUS);
		MatchReportContent.Score mustScore = score(items, RequirementType.MUST, WEIGHT_MUST);
		MatchReportContent.Score bonusScore = score(items, RequirementType.BONUS, WEIGHT_BONUS);
		MatchReportContent.Suggestion suggestion = suggest(must, mustScore);

		return new MatchReportContent(requirementItems, must, bonus, mustScore, bonusScore, suggestion);
	}

	private List<String> reasonsFor(GapItem item) {
		List<String> reasons = new ArrayList<>();
		switch (item.status()) {
			case SATISFIED_WITH_EVIDENCE -> reasons.add("关联有效证据，已满足该要求");
			case SELF_REPORTED_NO_EVIDENCE -> reasons.add("用户自报满足，但暂无证据支撑");
			case NOT_MET -> reasons.add("用户标记为未满足");
			case INSUFFICIENT_INFO -> reasons.add("缺少资料，暂无法判断（不计入得分分母）");
			case PENDING_CONFIRMATION -> reasons.add("要求待确认，不参与匹配结论");
		}
		if (item.manualOverrideReason() != null && !item.manualOverrideReason().isBlank()) {
			reasons.add("人工修正：" + item.manualOverrideReason());
		}
		return reasons;
	}

	private MatchReportContent.Summary summarize(List<GapItem> items, RequirementType type) {
		List<GapItem> scoped = items.stream().filter(i -> i.requirement().getType() == type).toList();
		return new MatchReportContent.Summary(
			scoped.size(),
			(int) scoped.stream().filter(i -> i.status() == GapStatus.SATISFIED_WITH_EVIDENCE).count(),
			(int) scoped.stream().filter(i -> i.status() == GapStatus.SELF_REPORTED_NO_EVIDENCE).count(),
			(int) scoped.stream().filter(i -> i.status() == GapStatus.NOT_MET).count(),
			(int) scoped.stream().filter(i -> i.status() == GapStatus.INSUFFICIENT_INFO).count()
		);
	}

	private MatchReportContent.Score score(List<GapItem> items, RequirementType type, int weight) {
		double numerator = 0;
		double denominator = 0;
		for (GapItem item : items) {
			if (item.requirement().getType() != type) continue;
			double value = switch (item.status()) {
				case SATISFIED_WITH_EVIDENCE -> VALUE_SATISFIED;
				case SELF_REPORTED_NO_EVIDENCE -> VALUE_SELF_REPORTED;
				case NOT_MET -> VALUE_NOT_MET;
				// 缺少资料/待确认不计入分母：不按零分处理（PRD 9.1）
				case INSUFFICIENT_INFO, PENDING_CONFIRMATION -> -1;
			};
			if (value < 0) continue;
			numerator += value * weight;
			denominator += weight;
		}
		return new MatchReportContent.Score(numerator, denominator, weight);
	}

	private MatchReportContent.Suggestion suggest(MatchReportContent.Summary must, MatchReportContent.Score mustScore) {
		if (must.total() == 0) {
			return new MatchReportContent.Suggestion("NEED_MORE_INFO",
				List.of("尚无已确认的必须要求，请先在岗位详情确认要求后重新生成报告"));
		}
		if (mustScore.denominator() == 0) {
			return new MatchReportContent.Suggestion("NEED_MORE_INFO",
				List.of("必须要求均缺少资料，先补充自评与证据后再评估"));
		}
		if (must.notMet() > 0) {
			return new MatchReportContent.Suggestion("LOW_MATCH",
				List.of(must.notMet() + " 项必须要求未满足", "建议先补齐证据或调整目标岗位"));
		}
		if (must.satisfiedWithEvidence() == must.total()) {
			return new MatchReportContent.Suggestion("STRONG_MATCH",
				List.of("全部必须要求都有证据支撑", "可在准备包中复盘对应项目案例"));
		}
		return new MatchReportContent.Suggestion("PARTIAL_MATCH",
			List.of(must.satisfiedWithEvidence() + " 项必须要求有证据",
				must.selfReportedNoEvidence() + " 项自报无证据，" + must.insufficientInfo() + " 项资料不足"));
	}

	private String fingerprint(List<GapItem> items) {
		List<String> lines = items.stream()
			.map(i -> i.requirement().getType() + "|" + i.requirement().getNormalizedName() + "|" + i.status())
			.sorted(Comparator.naturalOrder())
			.toList();
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (byte b : hash) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private MatchReportView view(MatchReport report, boolean stale) {
		MatchReportContent content = fromJson(report.getReportJson());
		Map<String, Integer> weights = new LinkedHashMap<>();
		weights.put(RequirementType.MUST.name(), WEIGHT_MUST);
		weights.put(RequirementType.BONUS.name(), WEIGHT_BONUS);
		return new MatchReportView(
			report.getId(), report.getJobId(), report.getRuleVersion(), weights, report.getGeneratedAt(),
			content.mustSummary(), content.bonusSummary(), content.mustScore(), content.bonusScore(),
			content.suggestion(), content.requirements(), stale);
	}

	private String weightsJson() {
		Map<String, Integer> weights = new LinkedHashMap<>();
		weights.put(RequirementType.MUST.name(), WEIGHT_MUST);
		weights.put(RequirementType.BONUS.name(), WEIGHT_BONUS);
		return toJson(weights);
	}

	private String toJson(Object value) {
		try {
			return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value);
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private MatchReportContent fromJson(String json) {
		try {
			return JSON.readValue(json, MatchReportContent.class);
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** 读视图：快照 + 读取时计算的 stale 标记。 */
	public record MatchReportView(
		String id,
		String jobId,
		String ruleVersion,
		Map<String, Integer> weights,
		String generatedAt,
		MatchReportContent.Summary mustSummary,
		MatchReportContent.Summary bonusSummary,
		MatchReportContent.Score mustScore,
		MatchReportContent.Score bonusScore,
		MatchReportContent.Suggestion suggestion,
		List<MatchReportContent.Item> requirements,
		boolean stale
	) { }
}

package com.jobhub.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.ai.domain.AiJob;
import com.jobhub.ai.domain.AiJobItemStatus;
import com.jobhub.ai.domain.AiJobStatus;
import com.jobhub.ai.domain.AiJobType;
import com.jobhub.ai.domain.AiProvider;
import com.jobhub.ai.domain.ResumeSuggestion;
import com.jobhub.ai.infrastructure.AiJobItemMapper;
import com.jobhub.ai.infrastructure.AiJobMapper;
import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.evidence.domain.ProjectCase;
import com.jobhub.evidence.infrastructure.ProjectMapper;
import com.jobhub.job.domain.Job;
import com.jobhub.job.domain.JobRequirement;
import com.jobhub.job.infrastructure.JobMapper;
import com.jobhub.job.infrastructure.JobRequirementMapper;
import com.jobhub.skill.domain.SkillProfile;
import com.jobhub.skill.infrastructure.SkillProfileMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 简历定制草稿（PRD 9.4）。输入快照只包含已确认事实：岗位已确认要求 + 用户的项目案例与技能
 * （含自评）。AI 仅重写表达，建议必须引用快照内的 sourceId（溯源），不得新增未经确认的内容；
 * 已采纳来源在重新生成时排除，避免重复建议，且不影响既有条目。
 */
@Component
public class ResumeDraftHandler implements AiTaskHandler {
	public static final String PROMPT_VERSION = "RESUME_DRAFT_V1";
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final Set<String> VALID_SOURCE_TYPES = Set.of("PROJECT", "SKILL");

	private final JobMapper jobMapper;
	private final JobRequirementMapper requirementMapper;
	private final ProjectMapper projectMapper;
	private final SkillProfileMapper skillProfileMapper;
	private final AiJobMapper aiJobMapper;
	private final AiJobItemMapper aiJobItemMapper;

	public ResumeDraftHandler(JobMapper jobMapper, JobRequirementMapper requirementMapper,
			ProjectMapper projectMapper, SkillProfileMapper skillProfileMapper, AiJobMapper aiJobMapper,
			AiJobItemMapper aiJobItemMapper) {
		this.jobMapper = jobMapper;
		this.requirementMapper = requirementMapper;
		this.projectMapper = projectMapper;
		this.skillProfileMapper = skillProfileMapper;
		this.aiJobMapper = aiJobMapper;
		this.aiJobItemMapper = aiJobItemMapper;
	}

	@Override
	public AiJobType type() {
		return AiJobType.RESUME_DRAFT;
	}

	@Override
	public String promptVersion() {
		return PROMPT_VERSION;
	}

	@Override
	public String buildInputSnapshot(String objectId) {
		Job job = jobMapper.selectById(objectId);
		VersionCheck.requireFound(job, "Job", objectId);
		List<JobRequirement> confirmed = requirementMapper.selectConfirmedByJobId(objectId);
		List<ProjectCase> projects = projectMapper.selectAll();
		List<SkillProfile> skills = skillProfileMapper.selectAll();
		Set<String> excluded = acceptedSourceIds(objectId);

		List<Object> facts = new ArrayList<>();
		for (ProjectCase project : projects) {
			if (excluded.contains(project.getId())) {
				continue;
			}
			facts.add(new Fact("PROJECT", project.getId(), project.getTitle(), project.getScenario(),
					project.getApproach(), project.getProblemSolved(), project.getResultText(), null));
		}
		for (SkillProfile skill : skills) {
			if (skill.getSkillId() == null || excluded.contains(skill.getSkillId())) {
				continue;
			}
			facts.add(new Fact("SKILL", skill.getSkillId(), skill.getSkillName(), null, null, null, null,
					skill.getSelfLevel()));
		}
		if (facts.isEmpty()) {
			throw new BusinessRuleException("没有可引用的项目或技能事实，请先在「项目与证据」补充");
		}

		try {
			return JSON.writeValueAsString(new Snapshot(job.getCompanyName(), job.getTitle(),
					confirmed.stream()
						.map(r -> new ConfirmedRequirement(r.getType().name(),
								r.getNormalizedName() == null ? r.getRawText() : r.getNormalizedName()))
						.toList(), facts, excluded.stream().toList()));
		} catch (BusinessRuleException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new BusinessRuleException("简历事实快照构建失败：" + ex.getMessage());
		}
	}

	@Override
	public String buildSystemPrompt() {
		return """
			你是简历定制助手。基于给定的"已确认事实"（用户的项目案例与技能）和"目标岗位已确认要求"，
			为每条事实产出面向该岗位的简历表达建议。
			只输出 JSON 数组，不要输出任何其他文字、解释或 Markdown 围栏。
			数组元素格式：
			{"sourceType":"PROJECT 或 SKILL","sourceId":"必须原样引用事实清单中的 sourceId","sourceTitle":"事实标题","suggestedText":"面向目标岗位重写后的简历表达"}
			红线：只能改写给定事实的已有内容，不得新增任何未提及的项目、职责、量化指标或技术栈；
			sourceId 必须来自事实清单，不得编造；每条事实最多一条建议。""";
	}

	@Override
	public List<ResumeSuggestion> execute(AiJob job, AiProvider provider, AiChatClient client) {
		Snapshot snapshot = parseSnapshot(job.getInputSnapshot());
		String userPrompt = "目标岗位已确认要求：" + JSON.valueToTree(snapshot.confirmedRequirements())
				+ "\n已确认事实清单：" + JSON.valueToTree(snapshot.facts());
		String raw = client.complete(provider, buildSystemPrompt(), userPrompt);
		String json = extractJsonArray(raw);
		List<ResumeSuggestion> parsed = parseSuggestions(json);
		Set<String> factIds = new HashSet<>();
		for (Fact fact : snapshot.facts()) {
			factIds.add(String.valueOf(fact.sourceId()));
		}
		List<ResumeSuggestion> valid = new ArrayList<>();
		for (ResumeSuggestion suggestion : parsed) {
			if (suggestion == null || suggestion.sourceType() == null
					|| !VALID_SOURCE_TYPES.contains(suggestion.sourceType())) {
				continue;
			}
			if (suggestion.sourceId() == null || !factIds.contains(suggestion.sourceId())) {
				continue; // 引用了事实清单之外的对象 → 拒绝（不得新增未经确认内容）
			}
			if (suggestion.suggestedText() == null || suggestion.suggestedText().isBlank()) {
				continue;
			}
			valid.add(new ResumeSuggestion(suggestion.sourceType(), suggestion.sourceId(),
					suggestion.sourceTitle() == null ? "" : suggestion.sourceTitle(),
					suggestion.suggestedText().trim()));
		}
		if (valid.isEmpty()) {
			throw new IllegalStateException("模型未返回有效的简历建议");
		}
		return valid;
	}

	private Set<String> acceptedSourceIds(String jobId) {
		Set<String> excluded = new HashSet<>();
		for (AiJob prior : aiJobMapper.selectByObject(AiJobType.RESUME_DRAFT.name(), jobId)) {
			if (prior.getStatus() != AiJobStatus.SUCCEEDED) {
				continue;
			}
			for (var item : aiJobItemMapper.selectByJob(prior.getId())) {
				if (item.getStatus() != AiJobItemStatus.ACCEPTED) {
					continue;
				}
				String sourceId = readSourceId(item.getEditedPayloadJson());
				if (sourceId == null) {
					sourceId = readSourceId(item.getPayloadJson());
				}
				if (sourceId != null) {
					excluded.add(sourceId);
				}
			}
		}
		return excluded;
	}

	private String readSourceId(String payloadJson) {
		if (payloadJson == null || payloadJson.isBlank()) {
			return null;
		}
		try {
			JsonNode node = JSON.readTree(payloadJson);
			JsonNode sourceId = node.get("sourceId");
			return sourceId == null || sourceId.isNull() ? null : sourceId.asText();
		} catch (Exception ex) {
			return null;
		}
	}

	private Snapshot parseSnapshot(String snapshotJson) {
		try {
			return JSON.readValue(snapshotJson, Snapshot.class);
		} catch (Exception ex) {
			throw new IllegalStateException("输入快照解析失败：" + ex.getMessage(), ex);
		}
	}

	private List<ResumeSuggestion> parseSuggestions(String json) {
		try {
			return JSON.readValue(json,
					JSON.getTypeFactory().constructCollectionType(List.class, ResumeSuggestion.class));
		} catch (Exception ex) {
			throw new IllegalStateException("输出 JSON 解析失败：" + ex.getMessage(), ex);
		}
	}

	private String extractJsonArray(String raw) {
		int start = raw.indexOf('[');
		int end = raw.lastIndexOf(']');
		if (start < 0 || end <= start) {
			throw new IllegalStateException("输出中未找到 JSON 数组");
		}
		return raw.substring(start, end + 1);
	}

	public record Fact(String sourceType, String sourceId, String title, String scenario, String approach,
			String problemSolved, String result, Integer selfLevel) { }

	public record ConfirmedRequirement(String type, String text) { }

	public record Snapshot(String company, String title, List<ConfirmedRequirement> confirmedRequirements,
			List<Fact> facts, List<String> excludedSourceIds) { }
}

package com.jobhub.ai.application;

import com.jobhub.ai.domain.AiJob;
import com.jobhub.ai.domain.AiJobType;
import com.jobhub.ai.domain.AiItemPayload;
import com.jobhub.ai.domain.AiProvider;
import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.job.domain.Job;
import com.jobhub.job.infrastructure.JobMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * JD 结构化提取（PRD 9.2 首个 AI 场景）：把 JD 拆解为 MUST/BONUS 候选要求。
 * 输出解析宽松兼容 Markdown 代码围栏；type 非法或 rawText 为空的条目跳过，不编造内容。
 */
@Component
public class JdExtractionHandler implements AiTaskHandler {
	public static final String PROMPT_VERSION = "JD_EXTRACTION_V1";
	private static final Set<String> VALID_TYPES = Set.of("MUST", "BONUS");

	private final JobMapper jobMapper;

	public JdExtractionHandler(JobMapper jobMapper) {
		this.jobMapper = jobMapper;
	}

	@Override
	public AiJobType type() {
		return AiJobType.JD_EXTRACTION;
	}

	@Override
	public String promptVersion() {
		return PROMPT_VERSION;
	}

	@Override
	public String buildInputSnapshot(String objectId) {
		Job job = jobMapper.selectById(objectId);
		VersionCheck.requireFound(job, "Job", objectId);
		if (job.getJdRawText() == null || job.getJdRawText().isBlank()) {
			throw new BusinessRuleException("岗位缺少 JD 原文，无法执行 AI 提取");
		}
		return job.getJdRawText();
	}

	@Override
	public String buildSystemPrompt() {
		return """
			你是招聘 JD 分析助手。把给定的岗位描述拆解为可验证的岗位要求候选条目。
			只输出 JSON 数组，不要输出任何其他文字、解释或 Markdown 围栏。
			数组元素格式：
			{"type":"MUST","rawText":"要求原文，摘自 JD 并保持原句","normalizedName":"简短标准化名称，不超过 30 字","proficiencyText":"熟练度或能力要求，没有则空字符串"}
			规则：type 只能是 MUST（必须要求）或 BONUS（加分要求）；只提炼 JD 中明确出现的要求，不要编造；最多输出 %d 条。""".formatted(AiItemPayload.MAX_ITEMS);
	}

	@Override
	public List<AiItemPayload> execute(AiJob job, AiProvider provider, AiChatClient client) {
		String raw = client.complete(provider, buildSystemPrompt(), job.getInputSnapshot());
		String json = extractJsonArray(raw);
		List<AiItemPayload> parsed = AiItemPayload.parseList(json);
		List<AiItemPayload> valid = new ArrayList<>();
		for (AiItemPayload payload : parsed) {
			if (payload == null || payload.type() == null || !VALID_TYPES.contains(payload.type())) {
				continue;
			}
			if (payload.rawText() == null || payload.rawText().isBlank()) {
				continue;
			}
			String normalizedName = payload.normalizedName() == null || payload.normalizedName().isBlank()
				? truncate(payload.rawText().trim(), 50)
				: payload.normalizedName().trim();
			valid.add(new AiItemPayload(payload.type(), payload.rawText().trim(), normalizedName,
				payload.proficiencyText() == null ? null : payload.proficiencyText().trim()));
			if (valid.size() >= AiItemPayload.MAX_ITEMS) {
				break;
			}
		}
		if (valid.isEmpty()) {
			throw new IllegalStateException("模型未返回有效的岗位要求候选");
		}
		return valid;
	}

	/** 宽松截取 JSON 数组：剥离 Markdown 围栏与前后杂文本。 */
	private String extractJsonArray(String raw) {
		int start = raw.indexOf('[');
		int end = raw.lastIndexOf(']');
		if (start < 0 || end <= start) {
			throw new IllegalStateException("输出中未找到 JSON 数组");
		}
		return raw.substring(start, end + 1);
	}

	private String truncate(String value, int maxLength) {
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}
}

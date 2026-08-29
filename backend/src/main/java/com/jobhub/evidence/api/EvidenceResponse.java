package com.jobhub.evidence.api;

import com.jobhub.evidence.domain.Evidence;
import com.jobhub.evidence.domain.EvidenceType;
import java.util.List;

public record EvidenceResponse(
	String id,
	EvidenceType type,
	String title,
	String whereUsed,
	String problemSolved,
	String approach,
	String result,
	String urlOrPath,
	List<String> skillIds,
	long version
) {
	public static EvidenceResponse from(Evidence evidence) {
		return new EvidenceResponse(
			evidence.getId(),
			evidence.getType(),
			evidence.getTitle(),
			evidence.getWhereUsed(),
			evidence.getProblemSolved(),
			evidence.getApproach(),
			evidence.getResultText(),
			evidence.getUrlOrPath(),
			evidence.getSkillIds(),
			evidence.getVersion()
		);
	}
}

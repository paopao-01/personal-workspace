package com.jobhub.evidence.application;

import com.jobhub.evidence.domain.EvidenceType;
import java.util.List;

public record EvidenceCreateCommand(
	EvidenceType type,
	String title,
	String whereUsed,
	String problemSolved,
	String approach,
	String result,
	String urlOrPath,
	List<String> skillIds
) { }

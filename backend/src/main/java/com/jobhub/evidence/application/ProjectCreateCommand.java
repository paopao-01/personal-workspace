package com.jobhub.evidence.application;

import java.util.List;

public record ProjectCreateCommand(
	String title,
	String scenario,
	String approach,
	String problemSolved,
	String result,
	List<String> evidenceIds
) { }

package com.jobhub.interview.application;

import java.util.List;

public record PreparationItem(
	String type,
	String title,
	int priority,
	List<String> reasons,
	List<SourceRef> sourceRefs
) {
}

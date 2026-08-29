package com.jobhub.interview.api;

import com.jobhub.interview.application.PreparationItem;
import java.util.List;

public record PreparationItemResponse(
	String type,
	String title,
	int priority,
	List<String> reasons,
	List<SourceRefResponse> sourceRefs
) {
	public static PreparationItemResponse from(PreparationItem item) {
		return new PreparationItemResponse(
			item.type(),
			item.title(),
			item.priority(),
			item.reasons(),
			item.sourceRefs().stream().map(SourceRefResponse::from).toList()
		);
	}
}

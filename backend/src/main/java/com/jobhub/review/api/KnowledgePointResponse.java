package com.jobhub.review.api;

import com.jobhub.review.domain.KnowledgePoint;

public record KnowledgePointResponse(String id, String name, String category) {
	public static KnowledgePointResponse from(KnowledgePoint k) {
		return new KnowledgePointResponse(k.getId(), k.getName(), k.getCategory());
	}
}

package com.jobhub.datamanagement.api;

import com.jobhub.datamanagement.domain.TrashItem;
import java.util.List;

public record TrashItemResponse(
	String id,
	String resourceType,
	String resourceId,
	String displayName,
	List<String> impactSummary,
	String deletedAt,
	String expiresAt
) {
	public static TrashItemResponse from(TrashItem item) {
		return new TrashItemResponse(
			item.getId(),
			item.getResourceType(),
			item.getResourceId(),
			item.getDisplayName(),
			item.impactSummary(),
			item.getDeletedAt(),
			item.getExpiresAt()
		);
	}
}

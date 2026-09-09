package com.jobhub.skill.api;

import com.jobhub.skill.domain.SelfLevelHistoryEntry;
import java.util.List;

/**
 * 技能自评等级变更历史条目响应。fromLevel 为 null 表示首次自评无前值。
 */
public record SelfLevelHistoryEntryResponse(
		String id,
		Integer fromLevel,
		int toLevel,
		String reason,
		String occurredAt
) {
	public static SelfLevelHistoryEntryResponse from(SelfLevelHistoryEntry entry) {
		return new SelfLevelHistoryEntryResponse(
				entry.getId(),
				entry.getFromLevel(),
				entry.getToLevel(),
				entry.getReason(),
				entry.getOccurredAt()
		);
	}

	public static List<SelfLevelHistoryEntryResponse> fromList(List<SelfLevelHistoryEntry> entries) {
		return entries.stream().map(SelfLevelHistoryEntryResponse::from).toList();
	}
}

package com.jobhub.application.api;

import com.jobhub.application.domain.StatusLogEntry;
import com.jobhub.application.domain.ApplicationStatus;

import java.util.List;

/**
 * 投递状态历史响应。与 OpenAPI StatusLog schema 对齐。
 */
public record StatusLogResponse(
		String id,
		ApplicationStatus fromStatus,
		ApplicationStatus toStatus,
		String reason,
		String occurredAt
) {
	public static StatusLogResponse from(StatusLogEntry log) {
		if (log == null) return null;
		return new StatusLogResponse(
				log.getId(),
				log.getFromStatus(),
				log.getToStatus(),
				log.getReason(),
				log.getOccurredAt()
		);
	}

	public static List<StatusLogResponse> fromList(List<StatusLogEntry> logs) {
		return logs.stream().map(StatusLogResponse::from).toList();
	}
}

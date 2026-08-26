package com.jobhub.interview.api;

import com.jobhub.interview.domain.InterviewListItem;
import com.jobhub.interview.domain.InterviewMode;
import com.jobhub.interview.domain.InterviewResult;
import com.jobhub.interview.domain.InterviewScheduleStatus;

import java.util.List;

/**
 * GET /interviews 专用响应，保留面试字段并补充关联投递摘要。
 */
public record InterviewListItemResponse(
		String id,
		String applicationId,
		String roundName,
		String startsAt,
		String eventTimeZone,
		InterviewMode mode,
		String meetingUrlOrAddress,
		String contact,
		InterviewScheduleStatus scheduleStatus,
		InterviewResult result,
		List<String> preparationChecklist,
		String notes,
		long version,
		InterviewApplicationSummary application
) {
	public static InterviewListItemResponse from(InterviewListItem item, List<String> checklist) {
		return new InterviewListItemResponse(
				item.getId(), item.getApplicationId(), item.getRoundName(), item.getStartsAt(),
				item.getEventTimeZone(), item.getMode(), item.getMeetingUrlOrAddress(), item.getContact(),
				item.getScheduleStatus(), item.getResult(), checklist == null ? List.of() : checklist,
				item.getNotes(), item.getVersion(), InterviewApplicationSummary.from(item));
	}
}

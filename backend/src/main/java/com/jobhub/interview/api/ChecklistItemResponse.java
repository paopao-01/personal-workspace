package com.jobhub.interview.api;

import com.jobhub.interview.application.ChecklistItem;

public record ChecklistItemResponse(String id, String text, boolean completed) {
	public static ChecklistItemResponse from(ChecklistItem item) {
		return new ChecklistItemResponse(item.getId(), item.getText(), item.isCompleted());
	}
}

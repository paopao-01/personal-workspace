package com.jobhub.datamanagement.api;

import com.jobhub.datamanagement.domain.Notification;

public record NotificationResponse(
	String id,
	String reminderId,
	String title,
	String content,
	String readAt,
	String createdAt,
	java.util.List<ChannelDeliveryResponse> deliveries
) {
	public static NotificationResponse from(Notification notification) {
		return new NotificationResponse(
			notification.getId(),
			notification.getReminderId(),
			notification.getTitle(),
			notification.getContent(),
			notification.getReadAt(),
			notification.getCreatedAt(),
			notification.getDeliveries().stream().map(ChannelDeliveryResponse::from).toList()
		);
	}
}

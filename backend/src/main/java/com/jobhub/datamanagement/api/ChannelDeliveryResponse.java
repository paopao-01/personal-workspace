package com.jobhub.datamanagement.api;

import com.jobhub.datamanagement.domain.ChannelDelivery;

public record ChannelDeliveryResponse(
	String id,
	String notificationId,
	String channelType,
	String status,
	String failureReason,
	int attemptCount,
	String sentAt,
	String createdAt
) {
	public static ChannelDeliveryResponse from(ChannelDelivery delivery) {
		return new ChannelDeliveryResponse(
			delivery.getId(),
			delivery.getNotificationId(),
			delivery.getChannelType().name(),
			delivery.getStatus().name(),
			delivery.getFailureReason(),
			delivery.getAttemptCount(),
			delivery.getSentAt(),
			delivery.getCreatedAt()
		);
	}
}

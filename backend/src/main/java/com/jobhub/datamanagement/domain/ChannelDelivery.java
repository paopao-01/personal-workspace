package com.jobhub.datamanagement.domain;

/**
 * 渠道投递记录：每条站内通知在已启用渠道上的独立发送状态与失败原因（PRD 9.3）。
 */
public class ChannelDelivery {
	private String id;
	private String notificationId;
	private ChannelType channelType;
	private DeliveryStatus status;
	private String failureReason;
	private int attemptCount;
	private String sentAt;
	private String createdAt;
	private String updatedAt;

	public static ChannelDelivery create(String id, String notificationId, ChannelType channelType, String now) {
		ChannelDelivery delivery = new ChannelDelivery();
		delivery.id = id;
		delivery.notificationId = notificationId;
		delivery.channelType = channelType;
		delivery.status = DeliveryStatus.PENDING;
		delivery.createdAt = now;
		delivery.updatedAt = now;
		return delivery;
	}

	public void markSent(String now) {
		this.status = DeliveryStatus.SENT;
		this.failureReason = null;
		this.sentAt = now;
		this.updatedAt = now;
	}

	public void markFailed(String reason, String now) {
		this.status = DeliveryStatus.FAILED;
		this.failureReason = reason;
		this.updatedAt = now;
	}

	public String getId() { return id; }
	public String getNotificationId() { return notificationId; }
	public ChannelType getChannelType() { return channelType; }
	public DeliveryStatus getStatus() { return status; }
	public String getFailureReason() { return failureReason; }
	public int getAttemptCount() { return attemptCount; }
	public String getSentAt() { return sentAt; }
	public String getCreatedAt() { return createdAt; }
	public String getUpdatedAt() { return updatedAt; }
}

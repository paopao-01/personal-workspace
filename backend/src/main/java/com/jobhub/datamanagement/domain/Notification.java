package com.jobhub.datamanagement.domain;

/**
 * 站内通知。由到期提醒的调度单次生成（PENDING -> SENT 转移成功才插入）；
 * read_at 非空即已读，标记已读为单次转移，天然幂等。
 */
public class Notification {
	private String id;
	private String reminderId;
	private String title;
	private String content;
	private String readAt;
	private String createdAt;
	private java.util.List<ChannelDelivery> deliveries = java.util.List.of();

	public static Notification create(String id, String reminderId, String title, String content, String now) {
		Notification notification = new Notification();
		notification.id = id;
		notification.reminderId = reminderId;
		notification.title = title;
		notification.content = content;
		notification.createdAt = now;
		return notification;
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getReminderId() { return reminderId; }
	public void setReminderId(String reminderId) { this.reminderId = reminderId; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getContent() { return content; }
	public void setContent(String content) { this.content = content; }
	public String getReadAt() { return readAt; }
	public void setReadAt(String readAt) { this.readAt = readAt; }
	public String getCreatedAt() { return createdAt; }
	public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
	public java.util.List<ChannelDelivery> getDeliveries() { return deliveries == null ? java.util.List.of() : deliveries; }
	public void setDeliveries(java.util.List<ChannelDelivery> deliveries) {
		this.deliveries = deliveries == null ? java.util.List.of() : deliveries;
	}
}

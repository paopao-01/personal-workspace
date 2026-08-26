package com.jobhub.application.domain;

import java.util.Objects;

/**
 * 投递状态变更历史记录（对应 application_status_log 表）。
 * 不可覆盖历史：只允许 INSERT，禁止 UPDATE/DELETE 历史行。
 */
public class StatusLogEntry {

	private String id;
	private String applicationId;
	private ApplicationStatus fromStatus;  // nullable；首条记录可为空
	private ApplicationStatus toStatus;
	private String reason;
	private String idempotencyKey;
	private String occurredAt;

	public StatusLogEntry() { }

	public static StatusLogEntry create(String id, String applicationId, ApplicationStatus fromStatus,
									   ApplicationStatus toStatus, String reason, String idempotencyKey,
									   String occurredAt) {
		StatusLogEntry e = new StatusLogEntry();
		e.id = id;
		e.applicationId = applicationId;
		e.fromStatus = fromStatus;
		e.toStatus = toStatus;
		e.reason = reason;
		e.idempotencyKey = idempotencyKey;
		e.occurredAt = occurredAt;
		return e;
	}

	public String getId() { return id; }
	public String getApplicationId() { return applicationId; }
	public ApplicationStatus getFromStatus() { return fromStatus; }
	public ApplicationStatus getToStatus() { return toStatus; }
	public String getReason() { return reason; }
	public String getIdempotencyKey() { return idempotencyKey; }
	public String getOccurredAt() { return occurredAt; }

	public void setId(String id) { this.id = id; }
	public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
	public void setFromStatus(ApplicationStatus fromStatus) { this.fromStatus = fromStatus; }
	public void setToStatus(ApplicationStatus toStatus) { this.toStatus = toStatus; }
	public void setReason(String reason) { this.reason = reason; }
	public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
	public void setOccurredAt(String occurredAt) { this.occurredAt = occurredAt; }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof StatusLogEntry that)) return false;
		return Objects.equals(id, that.id);
	}

	@Override
	public int hashCode() { return Objects.hash(id); }
}

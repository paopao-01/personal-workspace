package com.jobhub.skill.domain;

/**
 * 技能自评等级变更历史条目（追加写只读投影）。
 * fromLevel 为 null 表示首次自评无前值；toLevel 为新值；reason 持久化用户填写理由（nullable）。
 */
public class SelfLevelHistoryEntry {
	private String id;
	private Integer fromLevel;
	private int toLevel;
	private String reason;
	private String occurredAt;

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public Integer getFromLevel() { return fromLevel; }
	public void setFromLevel(Integer fromLevel) { this.fromLevel = fromLevel; }
	public int getToLevel() { return toLevel; }
	public void setToLevel(int toLevel) { this.toLevel = toLevel; }
	public String getReason() { return reason; }
	public void setReason(String reason) { this.reason = reason; }
	public String getOccurredAt() { return occurredAt; }
	public void setOccurredAt(String occurredAt) { this.occurredAt = occurredAt; }
}

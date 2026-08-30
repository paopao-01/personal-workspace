package com.jobhub.job.application;

/** 用于差距计算的用户事实投影；null selfLevel 表示用户尚未提供自评。 */
public class RequirementSkillFact {
	private String skillId;
	private Integer selfLevel;
	private int evidenceCount;

	public String getSkillId() { return skillId; }
	public void setSkillId(String skillId) { this.skillId = skillId; }
	public Integer getSelfLevel() { return selfLevel; }
	public void setSelfLevel(Integer selfLevel) { this.selfLevel = selfLevel; }
	public int getEvidenceCount() { return evidenceCount; }
	public void setEvidenceCount(int evidenceCount) { this.evidenceCount = evidenceCount; }
}

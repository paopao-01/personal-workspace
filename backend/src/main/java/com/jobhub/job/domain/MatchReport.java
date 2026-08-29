package com.jobhub.job.domain;

/**
 * 匹配报告快照（追加式）。report_json 保存完整可解释内容（逐项状态、分组汇总、加权得分、建议），
 * input_fingerprint 为生成时的输入指纹；读取时重算对比得出 stale 标记（PRD 9.1：旧报告标记为可能过期）。
 */
public class MatchReport {
	private String id;
	private String jobId;
	private String ruleVersion;
	private String weightsJson;
	private String reportJson;
	private String inputFingerprint;
	private String generatedAt;

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getJobId() { return jobId; }
	public void setJobId(String jobId) { this.jobId = jobId; }
	public String getRuleVersion() { return ruleVersion; }
	public void setRuleVersion(String ruleVersion) { this.ruleVersion = ruleVersion; }
	public String getWeightsJson() { return weightsJson; }
	public void setWeightsJson(String weightsJson) { this.weightsJson = weightsJson; }
	public String getReportJson() { return reportJson; }
	public void setReportJson(String reportJson) { this.reportJson = reportJson; }
	public String getInputFingerprint() { return inputFingerprint; }
	public void setInputFingerprint(String inputFingerprint) { this.inputFingerprint = inputFingerprint; }
	public String getGeneratedAt() { return generatedAt; }
	public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
}

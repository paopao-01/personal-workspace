package com.jobhub.analytics.infrastructure;

/**
 * 效果对比聚合行。dimension 为渠道名或简历版本文本（resume_version 为空时为 null）。
 * mybatis map-underscore-to-camel-case 将 application_count/interview_count/offer_count 映射为对应字段。
 */
public class EffectivenessRow {
	private String dimension;
	private long applicationCount;
	private long interviewCount;
	private long offerCount;

	public String getDimension() { return dimension; }
	public long getApplicationCount() { return applicationCount; }
	public long getInterviewCount() { return interviewCount; }
	public long getOfferCount() { return offerCount; }

	public void setDimension(String dimension) { this.dimension = dimension; }
	public void setApplicationCount(long applicationCount) { this.applicationCount = applicationCount; }
	public void setInterviewCount(long interviewCount) { this.interviewCount = interviewCount; }
	public void setOfferCount(long offerCount) { this.offerCount = offerCount; }
}

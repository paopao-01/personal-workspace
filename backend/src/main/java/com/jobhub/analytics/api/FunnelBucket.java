package com.jobhub.analytics.api;

/**
 * 投递漏斗转化时间序列的一桶（只读）。
 *
 * date 为桶的分组键（day→date(applied_at) 如 2026-09-07，hour→strftime('%Y-%m-%dT%H:00:00Z', applied_at) 如 2026-09-07T13:00:00Z）；
 * total 为该桶全部非软删投递数（COUNT(*)，含 DRAFT）；applied 为已投递数（status != DRAFT，漏斗顶部）；
 * interviewed 为已进入面试数（status IN INTERVIEWING, OFFER，复用 §3.1 口径）；offered 为已拿 Offer 数（status = OFFER）；
 * interviewRate = interviewed/applied、offerRate = offered/applied（applied 为 0 时 0，不抛除零异常）。
 * 按 date 升序，只含有投递记录的桶不补 0。
 */
public record FunnelBucket(
		String date,
		long total,
		long applied,
		long interviewed,
		long offered,
		double interviewRate,
		double offerRate
) { }

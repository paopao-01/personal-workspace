package com.jobhub.application.application;

import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.error.ErrorCode;

/**
 * 同岗位已存在活动投递时创建二次投递被拒（AT-08 前半）。响应 409 DUPLICATE_APPLICATION。
 *
 * 注：allowDuplicate=true 的"创建成功"路径因 V1 部分唯一索引 uq_application_active_per_job
 * 限制无法实现（同岗位最多一条活动投递），本切片搁置，留待后续窗口用 V2 迁移重新设计。
 */
public class DuplicateApplicationException extends BusinessRuleException {

	public DuplicateApplicationException(String message) {
		super(ErrorCode.DUPLICATE_APPLICATION, message);
	}
}

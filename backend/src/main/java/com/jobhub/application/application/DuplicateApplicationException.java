package com.jobhub.application.application;

import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.error.ErrorCode;

/**
 * 同岗位已存在活动投递且用户未显式确认时，二次创建被拒（AT-08）。响应 409 DUPLICATE_APPLICATION。
 */
public class DuplicateApplicationException extends BusinessRuleException {

	public DuplicateApplicationException(String message) {
		super(ErrorCode.DUPLICATE_APPLICATION, message);
	}
}

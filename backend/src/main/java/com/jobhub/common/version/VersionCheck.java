package com.jobhub.common.version;

import com.jobhub.common.error.ErrorCode;
import com.jobhub.common.error.BusinessRuleException;

/**
 * 乐观锁工具。当 Mapper 返回受影响行数 0 时调用此方法抛出版本冲突异常。
 */
public final class VersionCheck {

	private VersionCheck() { }

	public static void requireAffected(int affected, long expectedVersion) {
		if (affected == 0) {
			throw new com.jobhub.common.error.VersionConflictException(expectedVersion);
		}
	}

	public static void requireAffected(int affected, long expectedVersion, String resourceType, String id) {
		if (affected == 0) {
			throw new com.jobhub.common.error.VersionConflictException(expectedVersion);
		}
	}

	public static void requireFound(Object entity, String resourceType, String id) {
		if (entity == null) {
			throw new com.jobhub.common.error.ResourceNotFoundException(resourceType, id);
		}
	}

	public static BusinessRuleException businessRule(String message) {
		return new BusinessRuleException(ErrorCode.BUSINESS_RULE_ERROR, message);
	}
}

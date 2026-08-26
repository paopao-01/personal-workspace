package com.jobhub.common.version;

import com.jobhub.common.error.ErrorCode;
import com.jobhub.common.error.BusinessRuleException;

/**
 * 乐观锁工具。当 Mapper 返回受影响行数 0 时调用此方法抛出版本冲突异常。
 */
public final class VersionCheck {

	private VersionCheck() { }

	/**
	 * 乐观锁工具。当 Mapper 返回受影响行数 0 时调用此方法抛出版本冲突异常。
	 *
	 * @param affected       Mapper 受影响行数（0 表示版本不匹配）
	 * @param currentVersion DB 中实体的当前版本（由调用方在 selectById 后传入，供客户端刷新重试）
	 */
	public static void requireAffected(int affected, long currentVersion) {
		if (affected == 0) {
			throw new com.jobhub.common.error.VersionConflictException(currentVersion);
		}
	}

	/**
	 * 重载：携带资源类型与 id 的版本冲突（信息更完整）。
	 */
	public static void requireAffected(int affected, long currentVersion, String resourceType, String id) {
		if (affected == 0) {
			throw new com.jobhub.common.error.VersionConflictException(currentVersion);
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

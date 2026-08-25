package com.jobhub.common.error;

/**
 * If-Match-Version 与当前版本不匹配。响应 409 VERSION_CONFLICT，并携带当前版本。
 */
public class VersionConflictException extends RuntimeException {

	private final long currentVersion;

	public VersionConflictException(long currentVersion) {
		super("Version conflict. Current version is " + currentVersion);
		this.currentVersion = currentVersion;
	}

	public long currentVersion() {
		return currentVersion;
	}
}

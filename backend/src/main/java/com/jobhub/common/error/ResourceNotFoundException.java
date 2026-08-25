package com.jobhub.common.error;

/**
 * 资源不存在或对当前用户不可见。响应 404 NOT_FOUND。
 */
public class ResourceNotFoundException extends RuntimeException {

	private final String resourceType;
	private final String id;

	public ResourceNotFoundException(String resourceType, String id) {
		super(resourceType + " not found: " + id);
		this.resourceType = resourceType;
		this.id = id;
	}

	public String resourceType() {
		return resourceType;
	}

	public String id() {
		return id;
	}
}

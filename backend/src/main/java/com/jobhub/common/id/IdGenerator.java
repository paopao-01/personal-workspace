package com.jobhub.common.id;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 应用层生成 UUID 主键。不依赖数据库自增或 rowid。
 */
@Component
public class IdGenerator {

	public String newId() {
		return UUID.randomUUID().toString();
	}
}

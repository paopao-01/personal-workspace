package com.jobhub.datamanagement.infrastructure;

import com.jobhub.datamanagement.domain.TrashItem;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface TrashMapper {
	@Insert("""
		INSERT INTO trash_item (
		  id, resource_type, resource_id, display_name, impact_summary_json, deleted_at, expires_at
		) VALUES (
		  #{id}, #{resourceType}, #{resourceId}, #{displayName}, #{impactSummaryJson}, #{deletedAt}, #{expiresAt}
		)
		""")
	int insert(@Param("id") String id, @Param("resourceType") String resourceType,
			@Param("resourceId") String resourceId, @Param("displayName") String displayName,
			@Param("impactSummaryJson") String impactSummaryJson, @Param("deletedAt") String deletedAt,
			@Param("expiresAt") String expiresAt);

	@Select("""
		SELECT id, resource_type, resource_id, display_name, impact_summary_json,
		       deleted_at, expires_at, restored_at, purged_at
		FROM trash_item
		WHERE restored_at IS NULL AND purged_at IS NULL
		ORDER BY deleted_at DESC
		""")
	List<TrashItem> selectActive();

	@Select("""
		SELECT id, resource_type, resource_id, display_name, impact_summary_json,
		       deleted_at, expires_at, restored_at, purged_at
		FROM trash_item
		WHERE id=#{id}
		""")
	TrashItem selectById(@Param("id") String id);

	@Update("""
		UPDATE trash_item
		SET restored_at=#{now}
		WHERE id=#{id} AND restored_at IS NULL AND purged_at IS NULL
		""")
	int markRestored(@Param("id") String id, @Param("now") String now);

	@Update("""
		UPDATE trash_item
		SET purged_at=#{now}
		WHERE id=#{id} AND restored_at IS NULL AND purged_at IS NULL
		""")
	int markPurged(@Param("id") String id, @Param("now") String now);
}

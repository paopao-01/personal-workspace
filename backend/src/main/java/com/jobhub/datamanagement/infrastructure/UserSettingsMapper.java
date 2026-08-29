package com.jobhub.datamanagement.infrastructure;

import com.jobhub.datamanagement.domain.UserSettings;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserSettingsMapper {
	@Select("""
		SELECT id, time_zone, default_reminder_offsets_json, version, created_at, updated_at
		FROM user_setting
		ORDER BY created_at
		LIMIT 1
		""")
	UserSettings selectFirst();

	@Insert("""
		INSERT INTO user_setting (id, user_id, time_zone, default_reminder_offsets_json, created_at, updated_at, version)
		VALUES (#{id}, #{userId}, #{settings.timeZone}, #{settings.defaultReminderOffsetsJson}, #{settings.createdAt}, #{settings.updatedAt}, 0)
		""")
	int insert(@Param("id") String id, @Param("userId") String userId, @Param("settings") UserSettings settings);

	@Update("""
		UPDATE user_setting
		SET time_zone=#{settings.timeZone},
		    default_reminder_offsets_json=#{settings.defaultReminderOffsetsJson},
		    updated_at=#{settings.updatedAt},
		    version=version+1
		WHERE id=#{settings.id}
		  AND version=#{expectedVersion}
		""")
	int update(@Param("settings") UserSettings settings, @Param("expectedVersion") long expectedVersion);
}

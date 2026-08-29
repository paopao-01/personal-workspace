package com.jobhub.datamanagement.infrastructure;

import com.jobhub.datamanagement.domain.ChannelType;
import com.jobhub.datamanagement.domain.NotificationChannel;
import org.apache.ibatis.annotations.*;

@Mapper
public interface NotificationChannelMapper {
	@Select("""
		SELECT id, channel_type AS channelType, enabled, config_json AS configJson,
		       created_at AS createdAt, updated_at AS updatedAt, version
		FROM notification_channel
		WHERE channel_type=#{type}
		""")
	NotificationChannel selectByType(@Param("type") ChannelType type);

	@Insert("""
		INSERT INTO notification_channel (id, channel_type, enabled, config_json, created_at, updated_at, version)
		VALUES (#{c.id}, #{c.channelType}, #{c.enabled}, #{c.configJson}, #{c.createdAt}, #{c.updatedAt}, 1)
		""")
	int insert(@Param("c") NotificationChannel channel);

	@Update("""
		UPDATE notification_channel
		SET enabled=#{c.enabled}, config_json=#{c.configJson}, updated_at=#{c.updatedAt}, version=version+1
		WHERE id=#{c.id} AND version=#{expectedVersion}
		""")
	int update(@Param("c") NotificationChannel channel, @Param("expectedVersion") long expectedVersion);

	@Select("SELECT channel_type FROM notification_channel WHERE enabled=1")
	java.util.List<String> selectEnabledTypes();
}

package com.jobhub.datamanagement.infrastructure;

import com.jobhub.datamanagement.domain.Notification;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface NotificationMapper {
	@Insert("""
		INSERT INTO notification (id, reminder_id, title, content, created_at)
		VALUES (#{id}, #{reminderId}, #{title}, #{content}, #{createdAt})
		""")
	int insert(@Param("id") String id, @Param("reminderId") String reminderId,
			@Param("title") String title, @Param("content") String content, @Param("createdAt") String createdAt);

	@Select("""
		SELECT id, reminder_id, title, content, read_at, created_at
		FROM notification
		ORDER BY created_at DESC, id
		LIMIT 100
		""")
	List<Notification> selectRecent();

	@Select("""
		SELECT id, reminder_id, title, content, read_at, created_at
		FROM notification
		WHERE id=#{id}
		""")
	Notification selectById(@Param("id") String id);

	@Update("UPDATE notification SET read_at=#{now} WHERE id=#{id} AND read_at IS NULL")
	int markRead(@Param("id") String id, @Param("now") String now);
}

package com.jobhub.datamanagement.infrastructure;

import com.jobhub.datamanagement.domain.ChannelDelivery;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ChannelDeliveryMapper {
	@Insert("""
		INSERT INTO channel_delivery (id, notification_id, channel_type, status, attempt_count, created_at, updated_at)
		VALUES (#{d.id}, #{d.notificationId}, #{d.channelType}, 'PENDING', 0, #{d.createdAt}, #{d.updatedAt})
		""")
	int insert(@Param("d") ChannelDelivery delivery);

	@Insert("""
		INSERT INTO channel_delivery (id, notification_id, channel_type, status, attempt_count, sent_at, created_at, updated_at)
		VALUES (#{d.id}, #{d.notificationId}, #{d.channelType}, 'SENT', 0, #{d.sentAt}, #{d.createdAt}, #{d.sentAt})
		""")
	int insertSent(@Param("d") ChannelDelivery delivery);

	@Select("""
		SELECT id, notification_id AS notificationId, channel_type AS channelType, status,
		       failure_reason AS failureReason, attempt_count AS attemptCount, sent_at AS sentAt,
		       created_at AS createdAt, updated_at AS updatedAt
		FROM channel_delivery
		WHERE id=#{id}
		""")
	ChannelDelivery selectById(@Param("id") String id);

	@Select("""
		SELECT id, notification_id AS notificationId, channel_type AS channelType, status,
		       failure_reason AS failureReason, attempt_count AS attemptCount, sent_at AS sentAt,
		       created_at AS createdAt, updated_at AS updatedAt
		FROM channel_delivery
		WHERE notification_id=#{notificationId} AND channel_type=#{type}
		""")
	ChannelDelivery selectByNotificationAndType(@Param("notificationId") String notificationId,
			@Param("type") String type);

	@Select("""
		SELECT id, notification_id AS notificationId, channel_type AS channelType, status,
		       failure_reason AS failureReason, attempt_count AS attemptCount, sent_at AS sentAt,
		       created_at AS createdAt, updated_at AS updatedAt
		FROM channel_delivery
		WHERE notification_id=#{notificationId}
		ORDER BY channel_type
		""")
	List<ChannelDelivery> selectByNotification(@Param("notificationId") String notificationId);

	@Select("""
		SELECT id, notification_id AS notificationId, channel_type AS channelType, status,
		       failure_reason AS failureReason, attempt_count AS attemptCount, sent_at AS sentAt,
		       created_at AS createdAt, updated_at AS updatedAt
		FROM channel_delivery
		WHERE channel_type='EMAIL' AND status='PENDING' AND attempt_count < #{maxAttempts}
		ORDER BY created_at, id
		LIMIT 200
		""")
	List<ChannelDelivery> selectPendingEmail(@Param("maxAttempts") int maxAttempts);

	@Select("""
		SELECT id, notification_id AS notificationId, channel_type AS channelType, status,
		       failure_reason AS failureReason, attempt_count AS attemptCount, sent_at AS sentAt,
		       created_at AS createdAt, updated_at AS updatedAt
		FROM channel_delivery
		WHERE channel_type='WEBHOOK' AND status='PENDING' AND attempt_count < #{maxAttempts}
		ORDER BY created_at, id
		LIMIT 200
		""")
	List<ChannelDelivery> selectPendingWebhook(@Param("maxAttempts") int maxAttempts);

	@Update("""
		UPDATE channel_delivery
		SET status='SENT', failure_reason=NULL, sent_at=#{now}, updated_at=#{now}
		WHERE id=#{id} AND status='PENDING'
		""")
	int markSent(@Param("id") String id, @Param("now") String now);

	@Update("""
		UPDATE channel_delivery
		SET status = CASE WHEN attempt_count + 1 >= #{maxAttempts} THEN 'FAILED' ELSE 'PENDING' END,
		    failure_reason=#{reason}, attempt_count=attempt_count+1, updated_at=#{now}
		WHERE id=#{id} AND status='PENDING'
		""")
	int markFailed(@Param("id") String id, @Param("reason") String reason, @Param("now") String now,
			@Param("maxAttempts") int maxAttempts);
}

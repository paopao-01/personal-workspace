package com.jobhub.datamanagement.application;

import com.jobhub.common.error.ResourceNotFoundException;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.datamanagement.domain.ChannelDelivery;
import com.jobhub.datamanagement.domain.ChannelType;
import com.jobhub.datamanagement.domain.Notification;
import com.jobhub.datamanagement.infrastructure.ChannelDeliveryMapper;
import com.jobhub.datamanagement.infrastructure.NotificationChannelMapper;
import com.jobhub.datamanagement.infrastructure.NotificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class NotificationService {
	private final NotificationMapper notificationMapper;
	private final IdGenerator ids;
	private final UtcTime time;

	public NotificationService(NotificationMapper notificationMapper, NotificationChannelMapper channelMapper,
			ChannelDeliveryMapper deliveryMapper, IdGenerator ids, UtcTime time) {
		this.notificationMapper = notificationMapper;
		this.channelMapper = channelMapper;
		this.deliveryMapper = deliveryMapper;
		this.ids = ids;
		this.time = time;
	}

	private final NotificationChannelMapper channelMapper;
	private final ChannelDeliveryMapper deliveryMapper;

	/** 供到期提醒调度调用：写一条站内通知，并为已启用渠道生成 PENDING 渠道投递记录（PRD 9.3）。 */
	public void createFromReminder(String reminderId, String title, String content) {
		Notification existing = notificationMapper.selectByReminderId(reminderId);
		String notificationId;
		if (existing == null) {
			notificationId = ids.newId();
			notificationMapper.insert(notificationId, reminderId, title, content, time.now());
		} else {
			notificationId = existing.getId();
		}
		for (String enabledType : channelMapper.selectEnabledTypes()) {
			ChannelType type = ChannelType.valueOf(enabledType);
			if (deliveryMapper.selectByNotificationAndType(notificationId, type.name()) == null) {
				ChannelDelivery delivery = ChannelDelivery.create(ids.newId(), notificationId, type, time.now());
				deliveryMapper.insert(delivery);
			}
		}
	}

	/** 按 id 查询通知（含渠道投递状态）；不存在返回 404。 */
	public Notification get(String notificationId) {
		Notification notification = notificationMapper.selectById(notificationId);
		VersionCheck.requireFound(notification, "Notification", notificationId);
		notification.setDeliveries(deliveryMapper.selectByNotification(notificationId));
		return notification;
	}

	public List<Notification> list() {
		return notificationMapper.selectRecent().stream()
			.map(notification -> {
				notification.setDeliveries(deliveryMapper.selectByNotification(notification.getId()));
				return notification;
			})
			.toList();
	}

	/**
	 * 标记已读。已读通知重复调用返回当前状态（幂等），不存在返回 404。
	 */
	@Transactional
	public Notification markRead(String notificationId) {
		Notification notification = notificationMapper.selectById(notificationId);
		VersionCheck.requireFound(notification, "Notification", notificationId);
		if (notification.getReadAt() == null) {
			if (notificationMapper.markRead(notificationId, time.now()) == 0) {
				throw new ResourceNotFoundException("Notification", notificationId);
			}
		}
		return notificationMapper.selectById(notificationId);
	}
}

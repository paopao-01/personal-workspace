package com.jobhub.datamanagement.application;

import com.jobhub.common.error.ResourceNotFoundException;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.datamanagement.domain.Notification;
import com.jobhub.datamanagement.infrastructure.NotificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class NotificationService {
	private final NotificationMapper notificationMapper;
	private final IdGenerator ids;
	private final UtcTime time;

	public NotificationService(NotificationMapper notificationMapper, IdGenerator ids, UtcTime time) {
		this.notificationMapper = notificationMapper;
		this.ids = ids;
		this.time = time;
	}

	/** 供到期提醒调度调用：写一条站内通知。 */
	public void createFromReminder(String reminderId, String title, String content) {
		notificationMapper.insert(ids.newId(), reminderId, title, content, time.now());
	}

	public List<Notification> list() {
		return notificationMapper.selectRecent();
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

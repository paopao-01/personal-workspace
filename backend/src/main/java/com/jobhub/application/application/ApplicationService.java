package com.jobhub.application.application;

import com.jobhub.application.domain.Application;
import com.jobhub.application.domain.ApplicationStatus;
import com.jobhub.application.domain.StatusLogEntry;
import com.jobhub.application.infrastructure.ApplicationMapper;
import com.jobhub.application.infrastructure.StatusLogMapper;
import com.jobhub.common.audit.AuditLogEntry;
import com.jobhub.common.audit.infrastructure.AuditLogMapper;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.job.domain.Job;
import com.jobhub.job.infrastructure.JobMapper;
import com.jobhub.datamanagement.application.TrashService;
import com.jobhub.common.error.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 投递用例服务。事务边界：create/transition/update 在单事务内完成 application_record 变更 + application_status_log 写入。
 *
 * 状态转换：实体 transition 方法校验矩阵，service 负责持久化 + 写不可覆盖历史日志 + 版本递增。
 * 非法转换在实体方法内抛 IllegalStateTransitionException，@Transactional 回滚 → 零副作用（AT-06）。
 */
@Service
public class ApplicationService {

	private final ApplicationMapper applicationMapper;
	private final StatusLogMapper statusLogMapper;
	private final AuditLogMapper auditLogMapper;
	private final JobMapper jobMapper;
	private final IdGenerator idGenerator;
	private final UtcTime utcTime;
	private final TrashService trashService;

	public ApplicationService(ApplicationMapper applicationMapper, StatusLogMapper statusLogMapper,
							 AuditLogMapper auditLogMapper,
								 JobMapper jobMapper, IdGenerator idGenerator, UtcTime utcTime, TrashService trashService) {
		this.applicationMapper = applicationMapper;
		this.statusLogMapper = statusLogMapper;
		this.auditLogMapper = auditLogMapper;
		this.jobMapper = jobMapper;
		this.idGenerator = idGenerator;
		this.utcTime = utcTime;
		this.trashService = trashService;
	}

	@Transactional
	public Application create(ApplicationCreateCommand cmd) {
		// 校验岗位存在
		Job job = jobMapper.selectById(cmd.jobId());
		VersionCheck.requireFound(job, "Job", cmd.jobId());

		// 二次投递检测（应用层先查，唯一索引兜底）
		Application existing = applicationMapper.selectActiveByJobId(cmd.jobId());
		boolean secondaryApplication = existing != null;
		if (secondaryApplication && !cmd.allowDuplicate()) {
			throw new DuplicateApplicationException(
					"Active application already exists for job " + cmd.jobId()
							+ "; set allowDuplicate=true to confirm a secondary application");
		}

		String id = idGenerator.newId();
		String now = utcTime.now();
		Application app = Application.create(id, cmd.jobId(), cmd.appliedAt(), cmd.channel(),
				cmd.resumeVersion(), cmd.expectedSalary(), cmd.contact(),
				cmd.nextAction(), cmd.nextActionDueAt(), cmd.notes(),
				secondaryApplication ? now : null, now);
		applicationMapper.insert(app);
		if (secondaryApplication) {
			auditLogMapper.insert(AuditLogEntry.secondaryApplicationConfirmation(
					idGenerator.newId(), id, now));
		}
		return applicationMapper.selectById(id);
	}

	public Application get(String id) {
		Application app = applicationMapper.selectById(id);
		VersionCheck.requireFound(app, "Application", id);
		return app;
	}

	public ApplicationDetail getDetail(String id) {
		Application app = applicationMapper.selectById(id);
		VersionCheck.requireFound(app, "Application", id);
		Job job = jobMapper.selectById(app.getJobId());  // 可为 null（岗位被删除时）
		List<StatusLogEntry> history = statusLogMapper.selectByApplication(id);
		// interviews 本切片为空（面试模块未实现），由 api 层 ApplicationDetailResponse 填充空数组
		return new ApplicationDetail(app, job, history);
	}

	public ApplicationListResult list(ApplicationListQuery query) {
		String now = utcTime.now();
		long total = applicationMapper.selectPageCount(query.status(), query.overdueActionOnly(), now);
		int offset = (query.page() - 1) * query.pageSize();
		List<Application> items = applicationMapper.selectPage(
				query.status(), query.overdueActionOnly(), now, query.pageSize(), offset);
		return new ApplicationListResult(items, total, query.page(), query.pageSize());
	}

	@Transactional
	public Application update(String id, long expectedVersion, ApplicationUpdateCommand cmd) {
		Application app = applicationMapper.selectById(id);
		VersionCheck.requireFound(app, "Application", id);
		app.updateMeta(cmd.channel(), cmd.resumeVersion(), cmd.expectedSalary(), cmd.contact(),
				cmd.nextAction(), cmd.nextActionDueAt(), cmd.rejectionReason(), cmd.notes(), utcTime.now());
		int affected = applicationMapper.updateMetaByIdAndVersion(app, expectedVersion);
		VersionCheck.requireAffected(affected, app.getVersion());
		int bumped = applicationMapper.bumpVersionByIdAndVersion(id, expectedVersion);
		VersionCheck.requireAffected(bumped, app.getVersion());
		return applicationMapper.selectById(id);
	}

	/**
	 * 状态转换。写 application_status_log（不可覆盖历史），版本递增。
	 * @param idempotencyKey 请求头 Idempotency-Key（可空），写入 status_log 便于审计追溯
	 */
	@Transactional
	public Application transition(String id, long expectedVersion, ApplicationTransitionCommand cmd,
								 String idempotencyKey) {
		Application app = applicationMapper.selectById(id);
		VersionCheck.requireFound(app, "Application", id);

		ApplicationStatus fromStatus = app.getStatus();
		app.transition(cmd.targetStatus(), cmd.allowOfferWithoutCompletedInterview(), utcTime.now());

		int affected = applicationMapper.updateStatusAndPreviousByIdAndVersion(app, expectedVersion);
		VersionCheck.requireAffected(affected, app.getVersion());
		int bumped = applicationMapper.bumpVersionByIdAndVersion(id, expectedVersion);
		VersionCheck.requireAffected(bumped, app.getVersion());

		// 写不可覆盖状态历史
		StatusLogEntry log = StatusLogEntry.create(
				idGenerator.newId(), id, fromStatus, cmd.targetStatus(),
				cmd.reason(), idempotencyKey, utcTime.now());
		statusLogMapper.insert(log);

		return applicationMapper.selectById(id);
	}

	public List<StatusLogEntry> listStatusHistory(String applicationId) {
		Application app = applicationMapper.selectById(applicationId);
		VersionCheck.requireFound(app, "Application", applicationId);
		return statusLogMapper.selectByApplication(applicationId);
	}

	@Transactional
	public void delete(String id, long expectedVersion) {
		Application application = get(id);
		if (applicationMapper.countActiveInterviews(id) > 0) {
			throw new BusinessRuleException("Delete related interviews before deleting this application");
		}
		String now = utcTime.now();
		VersionCheck.requireAffected(applicationMapper.softDelete(id, expectedVersion, now), application.getVersion());
		trashService.recordDeletion(TrashService.TYPE_APPLICATION, id, application.getChannel() + " 投递记录", List.of("保留状态历史"), now);
	}

	/** 投递详情聚合值对象（service 层组装，避免 controller 跨层调用多个 mapper）。 */
	public record ApplicationDetail(Application application, Job job,
									List<StatusLogEntry> statusHistory) { }
}

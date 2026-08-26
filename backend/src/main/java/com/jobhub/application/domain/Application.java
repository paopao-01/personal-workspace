package com.jobhub.application.domain;

import com.jobhub.common.error.IllegalStateTransitionException;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 投递聚合根。唯一投递状态来源（job_posting 不保存投递当前状态）。
 *
 * 状态机（02-state-machines.md §3）：
 *   DRAFT --submit--> APPLIED --resume-pass--> RESUME_PASSED --start-interviewing--> INTERVIEWING --offer--> OFFER
 *                                                                       └──reject--> REJECTED
 *   任意活动状态 --hold--> ON_HOLD --resume--> 原活动状态
 *   任意活动状态 --withdraw--> WITHDRAWN
 *   APPLIED / RESUME_PASSED / INTERVIEWING --reject--> REJECTED
 *
 * ON_HOLD 必须保存 previousActiveStatus，resume 时只能回到该状态；缺失则非法转换。
 * 进入 OFFER 前至少有一场 COMPLETED 面试，或 allowOfferWithoutCompletedInterview=true（本切片无面试模块，仅支持逃生舱）。
 *
 * 每次状态转换由 ApplicationService 写入 application_status_log（不可覆盖历史）。
 */
public class Application {

	/** 活动状态集合，与 V1 唯一索引 uq_application_active_per_job 的 WHERE 子句一致。 */
	public static final Set<ApplicationStatus> ACTIVE_STATUSES = Set.of(
			ApplicationStatus.DRAFT, ApplicationStatus.APPLIED,
			ApplicationStatus.RESUME_PASSED, ApplicationStatus.INTERVIEWING,
			ApplicationStatus.ON_HOLD);

	/** ON_HOLD 可保存的原活动状态白名单（V1 previous_active_status CHECK 约束）。 */
	private static final Set<ApplicationStatus> SAVABLE_PREVIOUS = Set.of(
			ApplicationStatus.DRAFT, ApplicationStatus.APPLIED,
			ApplicationStatus.RESUME_PASSED, ApplicationStatus.INTERVIEWING);

	/** 合法转换矩阵：当前状态 -> 允许的目标状态集合（不含 ON_HOLD 的 resume，resume 在方法内单独处理）。 */
	private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED;
	static {
		EnumMap<ApplicationStatus, Set<ApplicationStatus>> m = new EnumMap<>(ApplicationStatus.class);
		m.put(ApplicationStatus.DRAFT, Set.of(
				ApplicationStatus.APPLIED, ApplicationStatus.WITHDRAWN, ApplicationStatus.ON_HOLD));
		m.put(ApplicationStatus.APPLIED, Set.of(
				ApplicationStatus.RESUME_PASSED, ApplicationStatus.REJECTED,
				ApplicationStatus.WITHDRAWN, ApplicationStatus.ON_HOLD));
		m.put(ApplicationStatus.RESUME_PASSED, Set.of(
				ApplicationStatus.INTERVIEWING, ApplicationStatus.REJECTED,
				ApplicationStatus.WITHDRAWN, ApplicationStatus.ON_HOLD));
		m.put(ApplicationStatus.INTERVIEWING, Set.of(
				ApplicationStatus.OFFER, ApplicationStatus.REJECTED,
				ApplicationStatus.WITHDRAWN, ApplicationStatus.ON_HOLD));
		// ON_HOLD 仅允许 WITHDRAWN；resume 到 previousActiveStatus 在方法内单独处理
		m.put(ApplicationStatus.ON_HOLD, Set.of(ApplicationStatus.WITHDRAWN));
		m.put(ApplicationStatus.OFFER, Set.of());
		m.put(ApplicationStatus.REJECTED, Set.of());
		m.put(ApplicationStatus.WITHDRAWN, Set.of());
		ALLOWED = Collections.unmodifiableMap(m);
	}

	private String id;
	private String jobId;
	private ApplicationStatus status;
	private ApplicationStatus previousActiveStatus;  // nullable；ON_HOLD 专用
	private String appliedAt;
	private String channel;
	private String resumeVersion;
	private String expectedSalary;
	private String contact;
	private String nextAction;
	private String nextActionDueAt;
	private String rejectionReason;
	private String notes;
	private long version;
	private String createdAt;
	private String updatedAt;
	private String deletedAt;

	public Application() { }

	public static Application create(String id, String jobId, String appliedAt, String channel,
									 String resumeVersion, String expectedSalary, String contact,
									 String nextAction, String nextActionDueAt, String notes,
									 String now) {
		Application a = new Application();
		a.id = id;
		a.jobId = jobId;
		a.status = ApplicationStatus.DRAFT;
		a.appliedAt = appliedAt;
		a.channel = channel;
		a.resumeVersion = resumeVersion;
		a.expectedSalary = expectedSalary;
		a.contact = contact;
		a.nextAction = nextAction;
		a.nextActionDueAt = nextActionDueAt;
		a.notes = notes;
		a.version = 0;
		a.createdAt = now;
		a.updatedAt = now;
		return a;
	}

	/**
	 * 状态转换。校验矩阵 + ON_HOLD 往返 + OFFER 前置。非法转换抛 IllegalStateTransitionException（事务回滚，零副作用）。
	 *
	 * @param target                            目标状态（transition 端点 targetStatus 驱动）
	 * @param allowOfferWithoutCompletedInterview OFFER 逃生舱；本切片无面试模块，进 OFFER 必须为 true
	 */
	public Application transition(ApplicationStatus target, boolean allowOfferWithoutCompletedInterview, String now) {
		ApplicationStatus cur = this.status;

		// ON_HOLD 特殊分支：resume 必须回到保存的 previousActiveStatus
		if (cur == ApplicationStatus.ON_HOLD && target != ApplicationStatus.WITHDRAWN) {
			ApplicationStatus saved = this.previousActiveStatus;
			if (saved == null) {
				throw new IllegalStateTransitionException(cur.name(), target.name(),
						"ON_HOLD has no saved previousActiveStatus to resume");
			}
			if (target != saved) {
				throw new IllegalStateTransitionException(cur.name(), target.name(),
						"resume target must equal saved previousActiveStatus " + saved);
			}
			this.previousActiveStatus = null;
			this.status = saved;
			this.updatedAt = now;
			return this;
		}

		// OFFER 前置：INTERVIEWING -> OFFER 需逃生舱（本切片无 COMPLETED 面试可查）
		if (target == ApplicationStatus.OFFER && cur == ApplicationStatus.INTERVIEWING) {
			if (!allowOfferWithoutCompletedInterview) {
				throw new IllegalStateTransitionException(cur.name(), target.name(),
						"OFFER requires at least one COMPLETED interview or allowOfferWithoutCompletedInterview=true");
			}
		}

		// 通用矩阵校验
		if (!ALLOWED.getOrDefault(cur, Set.of()).contains(target)) {
			throw new IllegalStateTransitionException(cur.name(), target.name(),
					"transition from " + cur + " to " + target + " is not allowed");
		}

		// 进入 ON_HOLD：保存原活动状态
		if (target == ApplicationStatus.ON_HOLD) {
			if (!SAVABLE_PREVIOUS.contains(cur)) {
				throw new IllegalStateTransitionException(cur.name(), target.name(),
						"only an active status can be held");
			}
			this.previousActiveStatus = cur;
		}

		// 提交校验：DRAFT -> APPLIED 必须有 appliedAt 和 channel（schema NOT NULL 已保证，此处防御性校验）
		if (cur == ApplicationStatus.DRAFT && target == ApplicationStatus.APPLIED) {
			if (isBlank(this.appliedAt) || isBlank(this.channel)) {
				throw new IllegalStateTransitionException(cur.name(), target.name(),
						"submit to APPLIED requires appliedAt and channel");
			}
		}

		this.status = target;
		this.updatedAt = now;
		return this;
	}

	/**
	 * 更新元数据与下一步行动（不改 status）。全字段覆盖写（与 job updateBasicInfo 一致）；
	 * nextAction/nextActionDueAt/rejectionReason 传 null 即清空。
	 */
	public Application updateMeta(String channel, String resumeVersion, String expectedSalary,
								  String contact, String nextAction, String nextActionDueAt,
								  String rejectionReason, String notes, String now) {
		this.channel = channel;
		this.resumeVersion = resumeVersion;
		this.expectedSalary = expectedSalary;
		this.contact = contact;
		this.nextAction = nextAction;
		this.nextActionDueAt = nextActionDueAt;
		this.rejectionReason = rejectionReason;
		this.notes = notes;
		this.updatedAt = now;
		return this;
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	public boolean nextActionOverdue(String nowIso) {
		return nextActionDueAt != null && !nextActionDueAt.isBlank()
				&& Objects.requireNonNull(nextActionDueAt).compareTo(nowIso) < 0;
	}

	public boolean nextActionMissing() {
		return nextAction == null || nextAction.isBlank();
	}

	// --- getters ---

	public String getId() { return id; }
	public String getJobId() { return jobId; }
	public ApplicationStatus getStatus() { return status; }
	public ApplicationStatus getPreviousActiveStatus() { return previousActiveStatus; }
	public String getAppliedAt() { return appliedAt; }
	public String getChannel() { return channel; }
	public String getResumeVersion() { return resumeVersion; }
	public String getExpectedSalary() { return expectedSalary; }
	public String getContact() { return contact; }
	public String getNextAction() { return nextAction; }
	public String getNextActionDueAt() { return nextActionDueAt; }
	public String getRejectionReason() { return rejectionReason; }
	public String getNotes() { return notes; }
	public long getVersion() { return version; }
	public String getCreatedAt() { return createdAt; }
	public String getUpdatedAt() { return updatedAt; }
	public String getDeletedAt() { return deletedAt; }

	public void setVersion(long version) { this.version = version; }
	public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}

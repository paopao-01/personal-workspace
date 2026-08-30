package com.jobhub.dashboard.application;

import com.jobhub.application.domain.Application;
import com.jobhub.application.infrastructure.ApplicationMapper;
import com.jobhub.common.time.UtcTime;
import com.jobhub.job.domain.Job;
import com.jobhub.job.domain.JobDecisionStatus;
import com.jobhub.job.infrastructure.JobMapper;
import com.jobhub.interview.domain.Interview;
import com.jobhub.interview.domain.InterviewScheduleStatus;
import com.jobhub.interview.infrastructure.InterviewMapper;
import com.jobhub.review.application.ReviewService;
import com.jobhub.review.domain.WeakKnowledgePoint;
import com.jobhub.task.domain.LearningTask;
import com.jobhub.task.infrastructure.TaskMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 首页工作台聚合服务。AT-09：识别缺失与逾期下一步行动，逾期优先排序。
 *
 * 本切片只实现投递相关聚合：
 *   - actionItems 中 APPLICATION_ACTION_DUE：缺失行动 / 逾期行动 / 一般行动
 *   - activeApplications：进行中的活动投递
 *   - recentJobs：最近更新的岗位（复用 JobMapper.selectPage）
 *   - upcomingInterviews：未来已安排面试（按开始时间，最多 5 条）
 *   - weakKnowledgePoints：全时段薄弱知识点统计（复用复盘模块聚合查询）
 *
 * 行动缺失不阻断状态转换（02-state-machines.md §3），仅作为 dashboard 提示。
 */
@Service
public class DashboardService {

	/** ActionItem.type 枚举值（与 OpenAPI 对齐）。 */
	public static final String TYPE_APPLICATION_ACTION_DUE = "APPLICATION_ACTION_DUE";
	public static final String TYPE_REVIEW_DUE = "REVIEW_DUE";
	public static final String TYPE_TASK_DUE = "TASK_DUE";

	/** 逾期/缺失优先级（priority 越小越靠前）。 */
	private static final int PRIORITY_REVIEW_DUE = 1;
	private static final int PRIORITY_OVERDUE = 2;
	private static final int PRIORITY_MISSING = 3;
	private static final int PRIORITY_TASK_OVERDUE = 4;
	private static final int PRIORITY_TASK_DUE = 5;
	private static final int PRIORITY_NORMAL = 6;

	private static final int RECENT_JOBS_LIMIT = 10;
	private static final int UPCOMING_INTERVIEWS_LIMIT = 5;

	private final ApplicationMapper applicationMapper;
	private final JobMapper jobMapper;
	private final InterviewMapper interviewMapper;
	private final ReviewService reviewService;
	private final TaskMapper taskMapper;
	private final UtcTime utcTime;

	public DashboardService(ApplicationMapper applicationMapper, JobMapper jobMapper,
			InterviewMapper interviewMapper, ReviewService reviewService, TaskMapper taskMapper, UtcTime utcTime) {
		this.applicationMapper = applicationMapper;
		this.jobMapper = jobMapper;
		this.interviewMapper = interviewMapper;
		this.reviewService = reviewService;
		this.taskMapper = taskMapper;
		this.utcTime = utcTime;
	}

	public DashboardOverview getOverview() {
		String now = utcTime.now();
		List<Application> activeApps = applicationMapper.selectActiveForDashboard();

		// 批量取 job title，避免 N+1
		Map<String, Job> jobById = batchJobs(activeApps);

		List<ActionItem> actionItems = new ArrayList<>();
		for (Application app : activeApps) {
			ActionItem item = toActionItem(app, jobById.get(app.getJobId()), now);
			if (item != null) {
				actionItems.add(item);
			}
		}
		// recentJobs：复用 JobMapper.selectPage（按 updated_at DESC）
		List<Job> recentJobs = jobMapper.selectPage(null, null, null, null, null, null, RECENT_JOBS_LIMIT, 0);
		for (Job job : recentJobs) {
			if (job.getDecisionStatus() == JobDecisionStatus.TO_APPLY && activeApps.stream()
					.noneMatch(app -> app.getJobId().equals(job.getId()))) {
				actionItems.add(new ActionItem(
						job.getId(), TYPE_APPLICATION_ACTION_DUE,
						"为该岗位创建投递或安排下一步行动",
						null, PRIORITY_MISSING,
						new SourceRef("JOB", job.getId(), job.getTitle())));
			}
		}
		for (Interview interview : interviewMapper.selectCompletedNeedingReview()) {
			actionItems.add(new ActionItem(interview.getId(), TYPE_REVIEW_DUE,
					"完成「" + interview.getRoundName() + "」的面试复盘", interview.getStartsAt(), PRIORITY_REVIEW_DUE,
					new SourceRef("INTERVIEW", interview.getId(), interview.getRoundName())));
		}
		String dueUntil = Instant.parse(now).plus(Duration.ofDays(7)).toString();
		for (LearningTask task : taskMapper.selectDueForDashboard(dueUntil)) {
			boolean overdue = task.getDueAt().compareTo(now) < 0;
			actionItems.add(new ActionItem(task.getId(), TYPE_TASK_DUE,
					(overdue ? "逾期学习任务：" : "即将到期学习任务：") + task.getTitle(), task.getDueAt(),
					overdue ? PRIORITY_TASK_OVERDUE : PRIORITY_TASK_DUE,
					new SourceRef("TASK", task.getId(), task.getTitle())));
		}
		// 逾期(1) > 缺失(2) > 一般(3)；同优先级按 dueAt 升序（null 靠后）
		actionItems.sort(Comparator
				.comparingInt(ActionItem::priority)
				.thenComparing(a -> a.dueAt() == null ? "9" : a.dueAt()));

		List<Interview> upcomingInterviews = interviewMapper
				.selectUpcoming(now, "9999-12-31T23:59:59Z", InterviewScheduleStatus.SCHEDULED)
				.stream()
				.limit(UPCOMING_INTERVIEWS_LIMIT)
				.toList();

		List<WeakKnowledgePoint> weakKnowledgePoints = reviewService.weakKnowledgePoints(null, null, null);
		return new DashboardOverview(actionItems, activeApps, recentJobs, upcomingInterviews, weakKnowledgePoints);
	}

	private Map<String, Job> batchJobs(List<Application> apps) {
		List<String> jobIds = apps.stream().map(Application::getJobId).distinct().toList();
		if (jobIds.isEmpty()) {
			return Map.of();
		}
		return jobMapper.selectByIds(jobIds).stream()
				.collect(Collectors.toMap(Job::getId, j -> j, (a, b) -> a, LinkedHashMap::new));
	}

	/**
	 * 投递 -> ActionItem。缺失或逾期生成提示，未来行动也生成（供 dashboard 展示）。
	 * 全部活动投递均生成一项（缺失/逾期/未来三种），便于 AT-09 断言排序与提示。
	 */
	private ActionItem toActionItem(Application app, Job job, String now) {
		String jobTitle = job != null ? job.getTitle() : "未知岗位";
		String label = jobTitle;
		SourceRef ref = new SourceRef("APPLICATION", app.getId(), label);

		// 缺失行动
		if (app.nextActionMissing()) {
			return new ActionItem(
					app.getId(), TYPE_APPLICATION_ACTION_DUE,
					"补充「" + jobTitle + "」的下一步行动",
					null, PRIORITY_MISSING, ref);
		}

		// 逾期行动
		if (app.nextActionOverdue(now)) {
			long overdueDays = overdueDays(app.getNextActionDueAt(), now);
			return new ActionItem(
					app.getId(), TYPE_APPLICATION_ACTION_DUE,
					app.getNextAction() + "（已逾期 " + overdueDays + " 天）",
					app.getNextActionDueAt(), PRIORITY_OVERDUE, ref);
		}

		// 一般行动（未来到期或无截止时间）
		return new ActionItem(
				app.getId(), TYPE_APPLICATION_ACTION_DUE,
				app.getNextAction(),
				app.getNextActionDueAt(), PRIORITY_NORMAL, ref);
	}

	private long overdueDays(String dueAtIso, String nowIso) {
		try {
			Instant due = Instant.parse(dueAtIso);
			Instant now = Instant.parse(nowIso);
			return Math.max(0, Duration.between(due, now).toDays());
		} catch (Exception ex) {
			return 0;
		}
	}

	/** dashboard 聚合值对象。 */
	public record DashboardOverview(
			List<ActionItem> actionItems,
			List<Application> activeApplications,
			List<Job> recentJobs,
			List<Interview> upcomingInterviews,
			List<WeakKnowledgePoint> weakKnowledgePoints
	) { }

	public record ActionItem(
			String id,
			String type,
			String title,
			String dueAt,
			int priority,
			SourceRef sourceRef
	) { }

	public record SourceRef(String type, String id, String label) { }
}

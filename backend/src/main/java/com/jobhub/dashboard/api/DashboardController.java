package com.jobhub.dashboard.api;

import com.jobhub.application.api.ApplicationResponse;
import com.jobhub.dashboard.application.DashboardService;
import com.jobhub.dashboard.application.DashboardService.ActionItem;
import com.jobhub.dashboard.application.DashboardService.DashboardOverview;
import com.jobhub.dashboard.application.DashboardService.SourceRef;
import com.jobhub.job.api.JobResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页工作台端点。OpenAPI GET /dashboard → DashboardOverview。
 */
@RestController
@RequestMapping("/api")
public class DashboardController {

	private final DashboardService dashboardService;

	public DashboardController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	@GetMapping("/dashboard")
	public DashboardOverviewResponse overview() {
		return DashboardOverviewResponse.from(dashboardService.getOverview());
	}

	/** 与 OpenAPI DashboardOverview 对齐。 */
	public record DashboardOverviewResponse(
			List<ActionItemResponse> actionItems,
			List<UpcomingInterviewPlaceholder> upcomingInterviews,
			List<ApplicationResponse> activeApplications,
			List<WeakKnowledgePointPlaceholder> weakKnowledgePoints,
			List<JobResponse> recentJobs
	) {
		static DashboardOverviewResponse from(DashboardOverview o) {
			List<ActionItemResponse> actions = o.actionItems().stream()
					.map(ActionItemResponse::from).toList();
			List<ApplicationResponse> apps = o.activeApplications().stream()
					.map(ApplicationResponse::from).toList();
			List<JobResponse> jobs = o.recentJobs().stream()
					.map(JobResponse::from).toList();
			return new DashboardOverviewResponse(
					actions,
					List.of(),
					apps,
					List.of(),
					jobs);
		}
	}

	public record ActionItemResponse(
			String id,
			String type,
			String title,
			String dueAt,
			int priority,
			SourceRefResponse sourceRef
	) {
		static ActionItemResponse from(ActionItem a) {
			return new ActionItemResponse(a.id(), a.type(), a.title(), a.dueAt(), a.priority(),
					SourceRefResponse.from(a.sourceRef()));
		}
	}

	public record SourceRefResponse(String type, String id, String label) {
		static SourceRefResponse from(SourceRef s) {
			return new SourceRefResponse(s.type(), s.id(), s.label());
		}
	}

	/** 占位类型：面试/薄弱点模块未实现时返回空数组。 */
	public record UpcomingInterviewPlaceholder(String placeholder) { }
	public record WeakKnowledgePointPlaceholder(String placeholder) { }
}

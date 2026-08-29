package com.jobhub.interview.application;

import com.jobhub.application.domain.Application;
import com.jobhub.application.infrastructure.ApplicationMapper;
import com.jobhub.interview.domain.Interview;
import com.jobhub.interview.infrastructure.ChecklistMapper;
import com.jobhub.interview.infrastructure.PreparationMapper;
import com.jobhub.job.application.GapItem;
import com.jobhub.job.application.GapListService;
import com.jobhub.job.domain.GapStatus;
import com.jobhub.job.domain.JobRequirement;
import com.jobhub.review.domain.AnswerStatus;
import com.jobhub.review.domain.InterviewQuestion;
import com.jobhub.review.infrastructure.QuestionMapper;
import com.jobhub.task.domain.LearningTask;
import com.jobhub.task.infrastructure.TaskMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PreparationService {
	private static final String MISSING_PROJECT_ID = "00000000-0000-0000-0000-000000000000";

	private final InterviewService interviewService;
	private final ApplicationMapper applicationMapper;
	private final GapListService gapListService;
	private final PreparationMapper preparationMapper;
	private final QuestionMapper questionMapper;
	private final TaskMapper taskMapper;
	private final ChecklistMapper checklistMapper;

	public PreparationService(InterviewService interviewService, ApplicationMapper applicationMapper,
			GapListService gapListService, PreparationMapper preparationMapper, QuestionMapper questionMapper,
			TaskMapper taskMapper, ChecklistMapper checklistMapper) {
		this.interviewService = interviewService;
		this.applicationMapper = applicationMapper;
		this.gapListService = gapListService;
		this.preparationMapper = preparationMapper;
		this.questionMapper = questionMapper;
		this.taskMapper = taskMapper;
		this.checklistMapper = checklistMapper;
	}

	public PreparationPack getPreparationPack(String interviewId) {
		Interview interview = interviewService.get(interviewId);
		Application application = applicationMapper.selectById(interview.getApplicationId());
		List<GapItem> requirements = gapListService.getGapList(application.getJobId());
		List<ProjectCaseSummary> projectCases = projectCases(application.getJobId());
		List<InterviewQuestion> historicalQuestions = historicalQuestions(application.getJobId());
		List<LearningTask> openTasks = openTasks(application.getJobId());
		List<ChecklistItem> checklist = checklistMapper.selectItems(interviewId);
		List<PreparationItem> prioritizedItems = prioritizedItems(requirements, projectCases, historicalQuestions, openTasks, checklist);
		return new PreparationPack(interview, prioritizedItems, requirements, projectCases, historicalQuestions, openTasks, checklist);
	}

	private List<ProjectCaseSummary> projectCases(String jobId) {
		List<ProjectCaseSummary> projects = preparationMapper.selectProjectCasesForJob(jobId);
		for (ProjectCaseSummary project : projects) {
			project.setEvidenceRefs(preparationMapper.selectEvidenceForProject(project.getId()));
		}
		return projects.isEmpty() ? List.of(ProjectCaseSummary.missing()) : projects;
	}

	private List<InterviewQuestion> historicalQuestions(String jobId) {
		List<InterviewQuestion> questions = preparationMapper.selectHistoricalQuestionsForJob(jobId);
		for (InterviewQuestion question : questions) {
			question.setKnowledgePoints(questionMapper.selectKnowledgePoints(question.getId()));
		}
		return questions;
	}

	private List<LearningTask> openTasks(String jobId) {
		List<LearningTask> tasks = preparationMapper.selectOpenTasksForJob(jobId);
		for (LearningTask task : tasks) {
			task.setKnowledgePoints(taskMapper.selectKnowledgePoints(task.getId()));
		}
		return tasks;
	}

	private List<PreparationItem> prioritizedItems(List<GapItem> requirements, List<ProjectCaseSummary> projectCases,
			List<InterviewQuestion> questions, List<LearningTask> tasks, List<ChecklistItem> checklist) {
		List<PreparationItem> items = new ArrayList<>();
		int priority = 1;
		for (GapItem gap : requirements) {
			JobRequirement requirement = gap.requirement();
			List<String> reasons = new ArrayList<>();
			reasons.add("用户已确认岗位要求");
			if (gap.status() != GapStatus.SATISFIED_WITH_EVIDENCE) {
				reasons.add("当前差距状态：" + gap.status().name());
			}
			items.add(new PreparationItem("REQUIREMENT", titleFor(requirement), priority++, reasons,
				List.of(new SourceRef("JOB_REQUIREMENT", requirement.getId(), titleFor(requirement)))));
		}
		for (InterviewQuestion question : questions) {
			if (question.getAnswerStatus() == AnswerStatus.FULLY_ANSWERED) {
				continue;
			}
			items.add(new PreparationItem("QUESTION", question.getContent(), priority++,
				List.of("历史面试中未完全答出：" + question.getAnswerStatus().name()),
				List.of(new SourceRef("QUESTION", question.getId(), question.getContent()))));
		}
		for (LearningTask task : tasks) {
			List<String> reasons = new ArrayList<>();
			reasons.add("关联当前岗位知识点或历史问题的未完成任务");
			if (task.getDueAt() != null && !task.getDueAt().isBlank()) {
				reasons.add("任务截止时间：" + task.getDueAt());
			}
			items.add(new PreparationItem("TASK", task.getTitle(), priority++, reasons,
				List.of(new SourceRef("TASK", task.getId(), task.getTitle()))));
		}
		for (ProjectCaseSummary project : projectCases) {
			boolean missing = MISSING_PROJECT_ID.equals(project.getId());
			items.add(new PreparationItem("PROJECT_CASE", project.getTitle(), priority++,
				List.of(missing ? "未找到关联项目案例，需补充用户确认事实" : "可复用项目案例，关联已确认岗位要求"),
				List.of(new SourceRef("PROJECT_CASE", project.getId(), project.getTitle()))));
		}
		for (ChecklistItem item : checklist) {
			items.add(new PreparationItem("CHECKLIST", item.getText(), priority++,
				List.of("面试准备清单项"),
				List.of(new SourceRef("CHECKLIST", item.getId(), item.getText()))));
		}
		return items;
	}

	private String titleFor(JobRequirement requirement) {
		if (requirement.getNormalizedName() != null && !requirement.getNormalizedName().isBlank()) {
			return requirement.getNormalizedName();
		}
		return requirement.getRawText();
	}
}

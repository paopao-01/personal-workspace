package com.jobhub.interview.application;

import com.jobhub.interview.domain.Interview;
import com.jobhub.job.application.GapItem;
import com.jobhub.review.domain.InterviewQuestion;
import com.jobhub.task.domain.LearningTask;
import java.util.List;

public record PreparationPack(
	Interview interview,
	List<PreparationItem> prioritizedItems,
	List<GapItem> requirements,
	List<ProjectCaseSummary> projectCases,
	List<InterviewQuestion> historicalQuestions,
	List<LearningTask> openTasks,
	List<ChecklistItem> checklist
) {
}

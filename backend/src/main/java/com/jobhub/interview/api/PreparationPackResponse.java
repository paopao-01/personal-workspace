package com.jobhub.interview.api;

import com.jobhub.interview.application.PreparationPack;
import com.jobhub.job.api.GapItemResponse;
import com.jobhub.review.api.InterviewQuestionResponse;
import com.jobhub.task.api.LearningTaskResponse;
import java.util.List;

public record PreparationPackResponse(
	InterviewResponse interview,
	List<PreparationItemResponse> prioritizedItems,
	List<GapItemResponse> requirements,
	List<ProjectCaseSummaryResponse> projectCases,
	List<InterviewQuestionResponse> historicalQuestions,
	List<LearningTaskResponse> openTasks,
	List<ChecklistItemResponse> checklist
) {
	public static PreparationPackResponse from(PreparationPack pack) {
		return new PreparationPackResponse(
			InterviewResponse.from(pack.interview(), pack.checklist().stream().map(com.jobhub.interview.application.ChecklistItem::getText).toList()),
			pack.prioritizedItems().stream().map(PreparationItemResponse::from).toList(),
			pack.requirements().stream().map(GapItemResponse::from).toList(),
			pack.projectCases().stream().map(ProjectCaseSummaryResponse::from).toList(),
			pack.historicalQuestions().stream().map(InterviewQuestionResponse::from).toList(),
			pack.openTasks().stream().map(LearningTaskResponse::from).toList(),
			pack.checklist().stream().map(ChecklistItemResponse::from).toList()
		);
	}
}

package com.jobhub.interview.api;
import com.jobhub.interview.domain.InterviewMode;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
public record InterviewCreateRequest(@NotBlank String applicationId,@NotBlank String roundName,@NotBlank String startsAt,@NotBlank String eventTimeZone,InterviewMode mode,String meetingUrlOrAddress,String contact,List<String> preparationChecklist,String notes) { }

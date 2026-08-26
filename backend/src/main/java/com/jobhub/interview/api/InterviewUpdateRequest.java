package com.jobhub.interview.api;
import com.jobhub.interview.domain.*;
import java.util.List;
public record InterviewUpdateRequest(String roundName,InterviewMode mode,String meetingUrlOrAddress,String contact,List<String> preparationChecklist,String notes,InterviewResult result) { }

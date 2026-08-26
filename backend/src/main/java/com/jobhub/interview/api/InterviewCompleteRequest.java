package com.jobhub.interview.api;
import com.jobhub.interview.domain.InterviewResult;
public record InterviewCompleteRequest(InterviewResult result,String note) { }

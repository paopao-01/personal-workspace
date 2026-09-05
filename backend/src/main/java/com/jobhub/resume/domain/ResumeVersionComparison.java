package com.jobhub.resume.domain;
import java.util.List;
public record ResumeVersionComparison(ResumeVersion leftVersion,ResumeVersion rightVersion,List<Line> lines){public record Line(String kind,String text){}}

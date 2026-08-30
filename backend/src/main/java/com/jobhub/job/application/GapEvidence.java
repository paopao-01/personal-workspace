package com.jobhub.job.application;

/** 差距结论中可展示的证据引用，不读取引用位置本身。 */
public class GapEvidence {
	private String id;
	private String type;
	private String title;
	private String urlOrPath;
	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getType() { return type; }
	public void setType(String type) { this.type = type; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getUrlOrPath() { return urlOrPath; }
	public void setUrlOrPath(String urlOrPath) { this.urlOrPath = urlOrPath; }
}

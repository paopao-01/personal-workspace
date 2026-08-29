package com.jobhub.interview.application;

public class EvidenceReference {
	private String id;
	private String type;
	private String title;
	private String urlOrPath;
	private boolean trashed;

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getType() { return type; }
	public void setType(String type) { this.type = type; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getUrlOrPath() { return urlOrPath; }
	public void setUrlOrPath(String urlOrPath) { this.urlOrPath = urlOrPath; }
	public boolean isTrashed() { return trashed; }
	public void setTrashed(boolean trashed) { this.trashed = trashed; }
}

package com.hypersocket.scheduler;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.quartz.JobKey;
import org.quartz.Trigger;
import org.quartz.Trigger.TriggerState;

public class SchedulerResource {
	private String name;
	private String group;
	private String id;
	private Date nextFire;
	private Date lastFire;
	private String description;
	private TriggerState status = TriggerState.NONE;

	public SchedulerResource() {
	}

	public SchedulerResource(JobKey k) {
		name = k.getName();
		group = k.getGroup();
	}

	public SchedulerResource(Trigger trigger, TriggerState status) {
		/* NOTE: At the moment, job groups are not used, so ID is same as Name */
		setId(trigger.getJobKey().getName());
		setGroup(trigger.getJobKey().getGroup());
		setName(trigger.getJobKey().getName());
		setLastFire(trigger.getPreviousFireTime());
		setNextFire(trigger.getNextFireTime());
		this.status = status;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Date getNextFire() {
		return nextFire;
	}

	public void setNextFire(Date nextFire) {
		this.nextFire = nextFire;
	}

	public Date getLastFire() {
		return lastFire;
	}

	public void setLastFire(Date lastFire) {
		this.lastFire = lastFire;
	}

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public JobKey getJobKey() {
		return new JobKey(name, group);
	}

	public TriggerState getStatus() {
		return status;
	}

	public void setStatus(TriggerState status) {
		this.status = status;
	}

	public boolean matches(String searchPattern, String searchColumn) {
		/* TODO Support columns properly */
		return StringUtils.isBlank(searchPattern)
				|| (StringUtils.isNotBlank(searchPattern) && matchesString(id, searchPattern))
				|| (StringUtils.isNotBlank(searchPattern)  
						&& matchesString(name, searchPattern))
				|| (StringUtils.isNotBlank(searchPattern)  
						&& matchesString(group, searchPattern));
	}

	private boolean matchesString(String text, String pattern) {
		return StringUtils.isNotBlank(text) && text.toLowerCase().contains(pattern.toLowerCase());
	}

}

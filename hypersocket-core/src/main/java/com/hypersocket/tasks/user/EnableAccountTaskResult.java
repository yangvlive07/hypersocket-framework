package com.hypersocket.tasks.user;

import com.hypersocket.events.SystemEvent;
import com.hypersocket.realm.Realm;
import com.hypersocket.realm.RealmServiceImpl;
import com.hypersocket.tasks.Task;
import com.hypersocket.triggers.AbstractTaskResult;

public class EnableAccountTaskResult extends AbstractTaskResult {

	private static final long serialVersionUID = 712812739563857588L;

	public static final String EVENT_RESOURCE_KEY = "event.enableAccountResult";
	public EnableAccountTaskResult(Object source, Realm currentRealm, Task task) {
		super(source, EVENT_RESOURCE_KEY, true, currentRealm, task);
	}

	public EnableAccountTaskResult(Object source,Realm currentRealm, Task task, Throwable e) {
		super(source, EVENT_RESOURCE_KEY, e, currentRealm, task);
	}

	@Override
	public boolean isPublishable() {
		return false;
	}

	@Override
	public SystemEvent getEvent() {
		return this;
	}

	@Override
	public String getResourceBundle() {
		return RealmServiceImpl.RESOURCE_BUNDLE;
	}

}
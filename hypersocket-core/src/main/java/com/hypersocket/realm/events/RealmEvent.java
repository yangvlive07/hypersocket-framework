package com.hypersocket.realm.events;

import com.hypersocket.events.CommonAttributes;
import com.hypersocket.realm.Realm;
import com.hypersocket.session.Session;
import com.hypersocket.session.events.SessionEvent;

public class RealmEvent extends SessionEvent {

	private static final long serialVersionUID = 1767418948626283958L;
	Realm realm;
	
	public static final String ATTR_REALM_NAME = CommonAttributes.ATTR_REALM_NAME;
	
	public RealmEvent(Object source, String resourceKey, boolean success,
			Session session, Realm realm) {
		super(source, resourceKey, success, session);
		this.realm = realm;
	}

	public RealmEvent(Object source, String resourceKey, Throwable e,
			Session session, Realm realm) {
		super(source, resourceKey, e, session);
		this.realm = realm;
	}
	
	public RealmEvent(Object source, String resourceKey, boolean success,
			Session session, String realmName) {
		super(source, resourceKey, success, session);
		addAttribute(ATTR_REALM_NAME, realmName);
	}
	
	public RealmEvent(Object source, String resourceKey, Throwable e,
			Session session, String realmName) {
		super(source, resourceKey, e, session);
		addAttribute(ATTR_REALM_NAME, realmName);
	}

	public Realm getRealm() {
		return realm;
	}

}

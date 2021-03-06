package com.hypersocket.events;

import java.util.List;
import java.util.Set;

public interface EventService  {

	void registerEvent(Class<? extends SystemEvent> eventClass,
			String resourceBundle,
			EventPropertyCollector propertyCollector);

	void registerEvent(Class<? extends SystemEvent> eventClass,
			String resourceBundle);

	void publishEvent(SystemEvent event);

	EventDefinition getEventDefinition(String resourceKey);

	List<EventDefinition> getEvents();

	void registerEventDefinition(EventDefinition def);

	void publishDelayedEvents();

	void delayEvents(Boolean val);

	void rollbackDelayedEvents(boolean fireFailedEvents);

	void registerDynamicEvent(String resourceKey, String name, Set<String> attributeNames, String successMessage,
			String failureMessage, String warningMessage);

}

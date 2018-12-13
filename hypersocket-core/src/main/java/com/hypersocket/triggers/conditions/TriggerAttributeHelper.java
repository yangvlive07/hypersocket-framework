package com.hypersocket.triggers.conditions;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hypersocket.events.SystemEvent;

public class TriggerAttributeHelper {

	static Pattern eventPattern;
	
	static {
		eventPattern = Pattern.compile("event(\\d+):(.*)");
	}
	
	public static String getAttribute(String attribute, List<SystemEvent> sourceEvents) {
		
		SystemEvent lastEvent = sourceEvents.get(sourceEvents.size()-1);
		if(lastEvent.hasAttribute(attribute)) {
			return lastEvent.getAttribute(attribute);
		}
		
		Matcher m = eventPattern.matcher(attribute);
		if(m.matches()) {
			int index = Integer.parseInt(m.group(1));
			String realAttribute = m.group(2);
			return  sourceEvents.get(index).getAttribute(realAttribute);
		}

		return null;
	}

}
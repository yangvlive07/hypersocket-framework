package com.hypersocket.tasks.ip.resolve;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hypersocket.events.EventService;
import com.hypersocket.events.SystemEvent;
import com.hypersocket.i18n.I18NService;
import com.hypersocket.properties.ResourceTemplateRepository;
import com.hypersocket.properties.ResourceUtils;
import com.hypersocket.realm.Realm;
import com.hypersocket.tasks.AbstractTaskProvider;
import com.hypersocket.tasks.Task;
import com.hypersocket.tasks.TaskProviderService;
import com.hypersocket.triggers.MultipleTaskResults;
import com.hypersocket.triggers.AbstractTaskResult;
import com.hypersocket.triggers.ValidationException;

@Component
public class LookupIPTask extends AbstractTaskProvider {

	static Logger log = LoggerFactory.getLogger(LookupIPTask.class);
	
	public static final String TASK_RESOURCE_KEY = "lookupIPTask";

	public static final String RESOURCE_BUNDLE = "LookupIPTask";
	
	@Autowired
	LookupIPTaskRepository repository;

	@Autowired
	TaskProviderService taskService;

	@Autowired
	EventService eventService;

	@Autowired
	I18NService i18nService; 

	public LookupIPTask() {
	}
	
	@PostConstruct
	private void postConstruct() {
		taskService.registerTaskProvider(this);

		i18nService.registerBundle(RESOURCE_BUNDLE);

		eventService.registerEvent(LookupIPTaskResult.class,
				RESOURCE_BUNDLE);
	}

	@Override
	public String getResourceBundle() {
		return RESOURCE_BUNDLE;
	}

	@Override
	public String[] getResourceKeys() {
		return new String[] { TASK_RESOURCE_KEY };
	}

	@Override
	public void validate(Task task, Map<String, String> parameters)
			throws ValidationException {
		if(!parameters.containsKey("resolveIP.hostnames")) {
			throw new ValidationException("No hostnames set for Resolve IP task");
		}
	}

	@Override
	public AbstractTaskResult execute(Task task, Realm currentRealm, List<SystemEvent> event)
			throws ValidationException {

		String[] values = ResourceUtils.explodeValues(repository.getValue(task, "resolveIP.hostnames"));
		
		List<LookupIPTaskResult> results = new ArrayList<LookupIPTaskResult>();
		List<InetAddress> successfull = new ArrayList<InetAddress>();
		
		for(String val : values) {
			
			for(String hostname : ResourceUtils.explodeValues(processTokenReplacements(val, event))) {
				try {
					InetAddress addr = InetAddress.getByName(hostname);
					if(log.isDebugEnabled()) {
						log.debug("Resolved IP " + addr.getHostAddress() + " for host " + hostname);
					}
					successfull.add(addr);
				} catch (UnknownHostException e) {
					if(log.isErrorEnabled()) {
						log.error("Unable to resolve IP for host " + hostname, e);
					}
					results.add(new LookupIPTaskResult(this, hostname, e, currentRealm, task));
				}
			}
			
		}
		// Task is performed here
		List<String> hosts = new ArrayList<String>();
		List<String> ips = new ArrayList<String>();
		
		for(InetAddress addr : successfull) {
			hosts.add(addr.getHostName());
			ips.add(addr.getHostAddress());
		}
		
		LookupIPTaskResult successfullResult = new LookupIPTaskResult
				(this, hosts.toArray(new String[0]), ips.toArray(new String[0]), currentRealm, task);
		
		if(results.size() > 0) {
			results.add(0,  successfullResult);
			return new MultipleTaskResults(this, currentRealm, task, results.toArray(new AbstractTaskResult[0]));
		} else {
			return successfullResult;
		}
	}
	
	public String getResultResourceKey() {
		return LookupIPTaskResult.EVENT_RESOURCE_KEY;
	}

	@Override
	public ResourceTemplateRepository getRepository() {
		return repository;
	}

	@Override
	public boolean isSystem() {
		return false;
	}
}
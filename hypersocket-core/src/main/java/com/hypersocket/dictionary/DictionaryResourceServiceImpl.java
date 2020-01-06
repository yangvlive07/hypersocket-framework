package com.hypersocket.dictionary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpCookie;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;

import com.hypersocket.auth.AbstractAuthenticatedServiceImpl;
import com.hypersocket.config.SystemConfigurationService;
import com.hypersocket.dictionary.events.DictionaryResourceCreatedEvent;
import com.hypersocket.dictionary.events.DictionaryResourceDeletedEvent;
import com.hypersocket.dictionary.events.DictionaryResourceEvent;
import com.hypersocket.dictionary.events.DictionaryResourceUpdatedEvent;
import com.hypersocket.events.EventService;
import com.hypersocket.http.HttpUtilsImpl;
import com.hypersocket.i18n.I18NService;
import com.hypersocket.permissions.AccessDeniedException;
import com.hypersocket.permissions.PermissionCategory;
import com.hypersocket.permissions.PermissionService;
import com.hypersocket.properties.EntityResourcePropertyStore;
import com.hypersocket.properties.PropertyCategory;
import com.hypersocket.properties.ResourceUtils;
import com.hypersocket.resource.AbstractResourceServiceImpl;
import com.hypersocket.resource.ResourceConfirmationException;
import com.hypersocket.resource.ResourceCreationException;
import com.hypersocket.resource.ResourceException;
import com.hypersocket.resource.ResourceNotFoundException;
import com.hypersocket.tables.ColumnSort;
import com.hypersocket.transactions.TransactionService;

@Service
public class DictionaryResourceServiceImpl extends AbstractAuthenticatedServiceImpl
		implements DictionaryResourceService {

	public static final String RESOURCE_BUNDLE = "DictionaryResourceService";
	public static final String USER_AGENT = "Mozilla/5.0";
	public static final String METHOD_GET = "GET";
	public static final String METHOD_POST = "POST";

	static Logger log = LoggerFactory.getLogger(DictionaryResourceServiceImpl.class);

	@Autowired
	private DictionaryResourceRepository dictionaryRepository;

	@Autowired
	private TransactionService transactionService;

	@Autowired
	private DictionaryResourceRepository repository;

	@Autowired
	private I18NService i18nService;

	@Autowired
	private PermissionService permissionService;

	@Autowired
	private EventService eventService;

	@Autowired
	private HttpUtilsImpl httpUtils;

	@Autowired
	private SystemConfigurationService systemConfigurationService;

	@PostConstruct
	private void postConstruct() {

		i18nService.registerBundle(RESOURCE_BUNDLE);

		PermissionCategory cat = permissionService.registerPermissionCategory(RESOURCE_BUNDLE, "category.dictionarys");

		for (DictionaryResourcePermission p : DictionaryResourcePermission.values()) {
			permissionService.registerPermission(p, cat);
		}

		repository.loadPropertyTemplates("dictionaryResourceTemplate.xml");

		eventService.registerEvent(DictionaryResourceEvent.class, RESOURCE_BUNDLE);
		eventService.registerEvent(DictionaryResourceCreatedEvent.class, RESOURCE_BUNDLE);
		eventService.registerEvent(DictionaryResourceUpdatedEvent.class, RESOURCE_BUNDLE);
		eventService.registerEvent(DictionaryResourceDeletedEvent.class, RESOURCE_BUNDLE);

		EntityResourcePropertyStore.registerResourceService(Word.class, repository);

		new Thread() {
			public void run() {
				try {
					transactionService.doInTransaction(new TransactionCallback<Void>() {

						@Override
						public Void doInTransaction(TransactionStatus arg0) {
							dictionaryRepository.setup();
							return null;
						}

					});
				} catch (ResourceException e) {
					log.error("Could not setup dictionary", e);
				} catch (AccessDeniedException e) {
					log.error("Could not setup dictionary", e);
				}

			}
		}.start();
	}

	protected Class<Word> getResourceClass() {
		return Word.class;
	}

	protected void fireResourceCreationEvent(Word resource) {
		eventService.publishEvent(new DictionaryResourceCreatedEvent(this, getCurrentSession(), resource));
	}

	protected void fireResourceCreationEvent(Word resource, Throwable t) {
		eventService.publishEvent(new DictionaryResourceCreatedEvent(this, resource, t, getCurrentSession()));
	}

	protected void fireResourceUpdateEvent(Word resource) {
		eventService.publishEvent(new DictionaryResourceUpdatedEvent(this, getCurrentSession(), resource));
	}

	protected void fireResourceUpdateEvent(Word resource, Throwable t) {
		eventService.publishEvent(new DictionaryResourceUpdatedEvent(this, resource, t, getCurrentSession()));
	}

	protected void fireResourceDeletionEvent(Word resource) {
		eventService.publishEvent(new DictionaryResourceDeletedEvent(this, getCurrentSession(), resource));
	}

	protected void fireResourceDeletionEvent(Word resource, Throwable t) {
		eventService.publishEvent(new DictionaryResourceDeletedEvent(this, resource, t, getCurrentSession()));
	}

	@Override
	public Word updateResource(Word resource, Locale locale, String text)
			throws ResourceException, AccessDeniedException {

		resource.setText(text);
		resource.setLocale(locale);
		repository.saveResource(resource);
		try {
			repository.saveResource(resource);
			fireResourceUpdateEvent(resource);
		} catch (Throwable t) {
			log.error("Failed to update resource", t);
			if (t instanceof ResourceConfirmationException) {
				throw (ResourceConfirmationException) t;
			}
			fireResourceUpdateEvent(resource, t);
			if (t instanceof ResourceException) {
				throw (ResourceException) t;
			} else {
				throw new ResourceCreationException(AbstractResourceServiceImpl.RESOURCE_BUNDLE_DEFAULT,
						"generic.create.error", t.getMessage(), t);
			}
		}
		return resource;
	}

	@Override
	public Word createResource(Locale locale, String word) throws ResourceException, AccessDeniedException {

		Word resource = new Word();
		resource.setText(word);
		resource.setLocale(locale);

		assertPermission(DictionaryResourcePermission.CREATE);

		try {
			repository.saveResource(resource);
			fireResourceCreationEvent(resource);
		} catch (Throwable t) {
			log.error("Failed to create resource", t);
			if (t instanceof ResourceConfirmationException) {
				throw (ResourceConfirmationException) t;
			}
			fireResourceCreationEvent(resource, t);
			if (t instanceof ResourceException) {
				throw (ResourceException) t;
			} else {
				throw new ResourceCreationException(AbstractResourceServiceImpl.RESOURCE_BUNDLE_DEFAULT,
						"generic.create.error", t.getMessage(), t);
			}
		}

		return null;
	}

	@Override
	public Collection<PropertyCategory> getPropertyTemplate() throws AccessDeniedException {
		assertPermission(DictionaryResourcePermission.READ);
		return repository.getPropertyCategories(null);
	}

	@Override
	public Collection<PropertyCategory> getPropertyTemplate(Word resource) throws AccessDeniedException {
		assertPermission(DictionaryResourcePermission.READ);
		return repository.getPropertyCategories(resource);
	}

	@Override
	public String randomWord(Locale locale) {
		return dictionaryRepository.randomWord(locale);
	}

	@Override
	public boolean containsWord(Locale locale, String word) {
		return (systemConfigurationService.getBooleanValue("dictionary.blacklistBuiltIn")
				&& dictionaryRepository.containsWord(locale, word,
						systemConfigurationService.getBooleanValue("dictionary.caseInsenstive"), true))
				|| (systemConfigurationService.getBooleanValue("dictionary.blacklistApiCall")
						&& inRemote(locale, word));
	}

	protected HttpResponse authRemote(Locale locale, String word) {
		String method = processTokenReplacements(systemConfigurationService.getValue("dictionary.logonMethod"), locale,
				word, null);
		String url = processTokenReplacements(systemConfigurationService.getValue("dictionary.logonUrl"), locale, word, null);
		String responseContent = processTokenReplacements(
				systemConfigurationService.getValue("dictionary.logonResponseContent"), locale, word, null);
		String[] variables = systemConfigurationService.getValues("dictionary.logonVariables");
		String[] responses = systemConfigurationService.getValues("dictionary.logonResponseList");
		String[] headers = systemConfigurationService.getValues("dictionary.logonHeaders");
		boolean checkCertificate = systemConfigurationService.getBooleanValue("dictionary.certificate");

		for (int i = 0; i < responses.length; i++) {
			responses[i] = processTokenReplacements(responses[i], locale, word, null);
		}

		CloseableHttpClient client = null;
		HttpResponse response;
		try {

			client = httpUtils.createHttpClient(!checkCertificate);
			if (METHOD_GET.equals(method)) {
				url = encodeVars(locale, word, url, variables, null);
				HttpGet request = new HttpGet(url);
				request.addHeader("User-Agent", USER_AGENT);
				for (String header : headers) {
					request.addHeader(processTokenReplacements(ResourceUtils.getNamePairKey(header), locale, word, null),
							processTokenReplacements(ResourceUtils.getNamePairValue(header), locale, word, null));
				}
				response = client.execute(request);

			} else {
				HttpPost request = new HttpPost(url);

				List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(variables.length);
				for (String variable : variables) {
					String[] namePair = variable.split("=");
					nameValuePairs.add(new BasicNameValuePair(processTokenReplacements(namePair[0], locale, word, null),
							processTokenReplacements(namePair[1], locale, word, null)));
				}

				request.addHeader("User-Agent", USER_AGENT);
				for (String header : headers) {
					request.addHeader(processTokenReplacements(ResourceUtils.getNamePairKey(header), locale, word, null),
							processTokenReplacements(ResourceUtils.getNamePairValue(header), locale, word, null));
				}

				request.setEntity(new UrlEncodedFormEntity(nameValuePairs));
				response = client.execute(request);
			}

			HttpEntity entity = response.getEntity();
			String content = EntityUtils.toString(entity);

			boolean matchesResponse = responses.length == 0 ? true
					: Arrays.asList(responses).contains(Integer.toString(response.getStatusLine().getStatusCode()));
			boolean matchesContent = StringUtils.isBlank(content) || content.matches(responseContent);

			return matchesResponse && matchesContent ? response : null;

		} catch (Exception e) {
			log.error("Failed to fully process login " + method + " method for " + url + "variables: " + variables, e);
			return null;
		} finally {
			if (client != null) {
				try {
					client.close();
				} catch (IOException e) {
					log.error("Error closing login HttpClient instance for HTTP Task", e);
				}
			}
		}
	}

	protected boolean inRemote(Locale locale, String word) {

		HttpResponse logonResponse = null;
		if (systemConfigurationService.getBooleanValue("dictionary.logonRequired")) {
			logonResponse = authRemote(locale, word);
			if (logonResponse == null)
				return false;
		}

		String method = processTokenReplacements(systemConfigurationService.getValue("dictionary.method"), locale,
				word, logonResponse);
		String url = processTokenReplacements(systemConfigurationService.getValue("dictionary.url"), locale, word, logonResponse);
		String responseContent = processTokenReplacements(
				systemConfigurationService.getValue("dictionary.responseContent"), locale, word, logonResponse);
		String[] variables = systemConfigurationService.getValues("dictionary.variables");
		String[] responses = systemConfigurationService.getValues("dictionary.responseList");
		String[] headers = systemConfigurationService.getValues("dictionary.headers");
		boolean checkCertificate = systemConfigurationService.getBooleanValue("dictionary.certificate");

		for (int i = 0; i < responses.length; i++) {
			responses[i] = processTokenReplacements(responses[i], locale, word, logonResponse);
		}

		CloseableHttpClient client = null;
		HttpResponse response;
		try {

			client = httpUtils.createHttpClient(!checkCertificate);
			if (METHOD_GET.equals(method)) {
				url = encodeVars(locale, word, url, variables, logonResponse);
				HttpGet request = new HttpGet(url);
				request.addHeader("User-Agent", USER_AGENT);
				if(logonResponse != null) {
					for (Header h : logonResponse.getHeaders("Set-Cookie")) {
						List<HttpCookie> cookies = HttpCookie.parse(h.getValue());
						for (HttpCookie c : cookies) {
							request.addHeader("Cookie", c.toString());
						}
					}
				}
				
				for (String header : headers) {
					request.addHeader(processTokenReplacements(ResourceUtils.getNamePairKey(header), locale, word, logonResponse),
							processTokenReplacements(ResourceUtils.getNamePairValue(header), locale, word, logonResponse));
				}
				response = client.execute(request);

			} else {
				HttpPost request = new HttpPost(url);

				List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(variables.length);
				for (String variable : variables) {
					String[] namePair = variable.split("=");
					nameValuePairs.add(new BasicNameValuePair(processTokenReplacements(namePair[0], locale, word, logonResponse),
							processTokenReplacements(namePair[1], locale, word, logonResponse)));
				}

				request.addHeader("User-Agent", USER_AGENT);
				for (String header : headers) {
					request.addHeader(processTokenReplacements(ResourceUtils.getNamePairKey(header), locale, word, logonResponse),
							processTokenReplacements(ResourceUtils.getNamePairValue(header), locale, word, logonResponse));
				}

				request.setEntity(new UrlEncodedFormEntity(nameValuePairs));
				response = client.execute(request);
			}

			HttpEntity entity = response.getEntity();
			String content = EntityUtils.toString(entity);

			boolean matchesResponse = responses.length == 0 ? true
					: Arrays.asList(responses).contains(Integer.toString(response.getStatusLine().getStatusCode()));
			boolean matchesContent = StringUtils.isBlank(content) || content.matches(responseContent);

			return matchesResponse && matchesContent;

		} catch (Exception e) {
			log.error("Failed to fully process " + method + " method for " + url + "variables: " + variables, e);
			return false;
		} finally {
			if (client != null) {
				try {
					client.close();
				} catch (IOException e) {
					log.error("Error closing HttpClient instance for HTTP Task", e);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void deleteResource(Word resource) throws AccessDeniedException, ResourceException {
		assertPermission(DictionaryResourcePermission.DELETE);
		repository.deleteResource(resource);
	}

	@Override
	public Word getResourceById(long id) throws ResourceNotFoundException {
		return repository.getResourceById(id);
	}

	@Override
	public List<?> searchResources(Locale locale, String searchColumn, String searchPattern, int start, int length,
			ColumnSort[] sorting) throws AccessDeniedException {
		assertPermission(DictionaryResourcePermission.READ);
		return repository.search(locale, searchColumn, searchPattern, start, length, sorting);
	}

	@Override
	public Long getResourceCount(Locale locale, String searchColumn, String searchPattern)
			throws AccessDeniedException {
		assertPermission(DictionaryResourcePermission.READ);
		return repository.getResourceCount(locale, searchColumn, searchPattern);
	}

	@Override
	@Transactional(readOnly = false)
	public long importDictionary(Locale locale, Reader input, boolean ignoreDuplicates)
			throws ResourceException, IOException, AccessDeniedException {
		assertPermission(DictionaryResourcePermission.CREATE);
		BufferedReader r = new BufferedReader(input);
		String line;
		long l = 0;
		while ((line = r.readLine()) != null) {
			for (String word : line.split("\\s+")) {
				if (ignoreDuplicates && repository.containsWord(locale, word,
						systemConfigurationService.getBooleanValue("dictionary.caseInsenstive"), false))
					continue;
				createResource(locale, word);
				l++;
				if ((l % 100) == 0)
					repository.flush();

			}
		}
		return l;
	}

	@Override
	@Transactional
	public void deleteResources(List<Long> wordIds) throws ResourceException, AccessDeniedException {
		assertPermission(DictionaryResourcePermission.CREATE);
		repository.deleteResources(wordIds);
	}

	private String encodeVars(Locale locale, String word, String url, String[] variables, HttpResponse response)
			throws UnsupportedEncodingException {
		if (variables.length > 0) {
			url = url + "?";
			boolean first = true;
			for (String variable : variables) {
				if (!first) {
					url += "&";
				}
				first = false;
				String[] namePair = variable.split("=");
				url += URLEncoder.encode(processTokenReplacements(namePair[0], locale, word, response), "UTF-8") + "="
						+ URLEncoder.encode(processTokenReplacements(namePair[1], locale, word, response), "UTF-8");
			}
		}
		return url;
	}

	private String processTokenReplacements(String value, Locale locale, String word, HttpResponse response) {
		try {
			if (value == null)
				return null;
			value = value.replace("${word}", word);
			value = value.replace("${encodedWord}", URLEncoder.encode(word, "UTF-8"));
			value = value.replace("${locale}", locale == null ? "" : locale.toLanguageTag());
			if (response != null) {
				for (Header h : response.getHeaders("Set-Cookie")) {
					List<HttpCookie> cookies = HttpCookie.parse(h.getValue());
					for (HttpCookie c : cookies) {
						value = value.replace("${cookie." + c.getName() + "}", c.getValue());
					}
				}
			}
			return value;

		} catch (UnsupportedEncodingException e) {
			return value;
		}
	}
}

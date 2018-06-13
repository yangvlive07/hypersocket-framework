package com.hypersocket.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;

@SuppressWarnings("restriction")
public class TextProcessor {

	final static String START_SCRIPT_TAG = "$(script";
	final static String END_SCRIPT_TAG = "$(/script)";

	public interface Resolver {
		String evaluate(String variable);
	}

	class WhiteBlackListClassFilter implements ClassFilter {
		@Override
		public boolean exposeToScripts(String className) {
			boolean a = whitelistedClasses.contains(className) || whitelistedClasses.contains("*");
			boolean b = blacklistedClasses.contains(className) || blacklistedClasses.contains("*");
			return a & !b;
		}
	}

	final static Log LOG = LogFactory.getLog(TextProcessor.class);

	protected Map<String, Object> bindings = new HashMap<String, Object>();
	protected Map<String, String> map = new HashMap<String, String>();
	protected Map<String, List<Map<String, String>>> listMap = new HashMap<String, List<Map<String, String>>>();
	protected Map<String, Boolean> conditions = new HashMap<String, Boolean>();
	protected List<Resolver> resolvers = new LinkedList<>();
	protected Set<String> whitelistedClasses = new LinkedHashSet<>();
	protected Set<String> blacklistedClasses = new LinkedHashSet<>();

	protected Locale locale;
	private boolean unknownVariablesAreBlank = true;
	private boolean evaluateScripts;
	private boolean captureScriptErrors = true;

	public TextProcessor() {
		this(Locale.getDefault());
	}

	public TextProcessor(Locale locale) {
		this.locale = locale;
	}

	public void allowAllClasses() {
		whitelistedClasses.add("*");
		blacklistedClasses.remove("*");
	}

	public void denyAllClasses() {
		whitelistedClasses.remove("*");
		blacklistedClasses.add("*");
	}

	public void whitelistClassName(String className) {
		whitelistedClasses.add(className);
	}

	public void unwhitelistClassName(String className) {
		whitelistedClasses.remove(className);
	}

	public void blacklistClassName(String className) {
		blacklistedClasses.add(className);
	}

	public void unblacklistClassName(String className) {
		blacklistedClasses.remove(className);
	}

	public List<Map<String, String>> addList(String listName) {
		List<Map<String, String>> m = new ArrayList<Map<String, String>>();
		listMap.put(listName, m);
		return m;
	}

	public boolean isCaptureScriptErrors() {
		return captureScriptErrors;
	}

	public void setCaptureScriptErrors(boolean captureScriptErrors) {
		this.captureScriptErrors = captureScriptErrors;
	}

	public boolean isEvaluateScripts() {
		return evaluateScripts;
	}

	public void setEvaluateScripts(boolean evaluateScripts) {
		this.evaluateScripts = evaluateScripts;
	}

	public void enable(String key) {
		conditions.put(key, Boolean.TRUE);
	}

	public void disable(String key) {
		conditions.put(key, Boolean.FALSE);
	}

	public void clearLists() {
		listMap.clear();
	}

	public void addResolver(Resolver resolver) {
		resolvers.add(resolver);
	}

	public Map<String, String> addListRow(String listName) {
		List<Map<String, String>> l = listMap.get(listName);
		if (l == null) {
			l = addList(listName);
		}
		Map<String, String> vars = new HashMap<String, String>();
		l.add(vars);
		return vars;
	}

	public void addVariable(String variable, String value, Map<String, String> vars) {
		vars.put(variable, StringUtils.defaultString(value));
	}

	public void addBindings(String name, Object value) {
		bindings.put(name, value);
	}

	public void addVariable(String variable, String value) {
		addVariable(variable, value, map);
	}

	public boolean isUnknownVariablesAreBlank() {
		return unknownVariablesAreBlank;
	}

	public void setUnknownVariablesAreBlank(boolean unknownVariablesAreBlank) {
		this.unknownVariablesAreBlank = unknownVariablesAreBlank;
	}

	public String process(String text) {
		if (text == null) {
			// text = "!no text!";
			text = "";
		}

		// First look for conditional blocks and remove them if conditions are
		// not met
		while (true) {
			int openStart = text.indexOf("={");
			if (openStart == -1) {
				// No more conditions
				break;
			}
			int openEnd = text.indexOf("}", openStart);
			String conditionName = text.substring(openStart + 2, openEnd);
			boolean match = true;
			if (conditionName.startsWith("!")) {
				match = false;
				conditionName = conditionName.substring(1);
			}

			// Now look for the end of the condition
			String closeTag = "={/" + conditionName + "}";
			int closeStart = text.indexOf(closeTag);
			if (closeStart == -1) {
				LOG.warn("Invalid pattern in message template, no closing tabfor ={" + conditionName + "} was found");
				break;
			}

			if ((match && Boolean.TRUE.equals(conditions.get(conditionName)))
					|| (!match && !Boolean.TRUE.equals(conditions.get(conditionName)))) {
				text = text.substring(0, openStart) + text.substring(openEnd + 1, closeStart)
						+ text.substring(closeStart + closeTag.length());
			} else {
				text = text.substring(0, openStart) + text.substring(closeStart + closeTag.length());
			}
		}

		// First look for the repeating blocks and replace the content inside
		// those first
		for (String listName : listMap.keySet()) {

			// Repeat each list until no more occurrences
			while (true) {
				int start = text.indexOf("#{" + listName + "}");
				if (start != -1) {
					int end = text.indexOf("#{/" + listName + "}");
					if (end == -1) {
						LOG.warn("Invalid pattern in message template, no closing tag for #{" + listName
								+ "} was found");
						break;
					} else {
						StringBuilder bui = new StringBuilder();
						bui.append(text.substring(0, start));
						int i = text.indexOf('}', start);
						if (i == -1) {
							LOG.warn("Invalid pattern in message template, no closing bracket for #{" + listName
									+ "} was found");
							break;
						} else {
							String block = text.substring(i + 1, end);

							for (Map<String, String> v : listMap.get(listName)) {
								bui.append(processText(block, v));
							}

							int i2 = text.indexOf('}', end);
							if (i2 == -1) {
								LOG.warn("Invalid pattern in message template, no closing bracket for #{/" + listName
										+ "} was found");
								break;
							} else {
								bui.append(text.substring(i2 + 1));
							}

							text = bui.toString();

						}
					}
				} else {
					// No more occurrences of this list name
					break;
				}
			}
		}

		// Now the main content
		text = processText(text, map);
		
		// Evaluate scripts first so variable patterns in scripts are replaced
		// TODO provide a way to execute scripts BEFORE variable replacement
		ScriptEngineManager manager = new ScriptEngineManager();
		Map<String, ScriptEngine> engines = new HashMap<>();
		Map<String, Bindings> bindings = new HashMap<>();
		text = evaluateScripts(text, manager, engines, bindings);

		return text;
	}

	private String evaluateScripts(String text, ScriptEngineManager manager, Map<String, ScriptEngine> engines,
			Map<String, Bindings> bindings) {
		// Now look for script fragments
		if (evaluateScripts) {
			int startIndx = 0;
			while (true) {
				int openStart = text.indexOf(START_SCRIPT_TAG, startIndx);
				if (openStart == -1) {
					// No more conditions
					break;
				}

				int openStartEnd = text.indexOf(")", openStart);
				if (openStartEnd == -1) {
					startIndx = START_SCRIPT_TAG.length() + 1;
					continue;
				}

				String lang = text.substring(openStart + START_SCRIPT_TAG.length() + 1, openStartEnd).trim();
				if (StringUtils.isBlank(lang) || lang.equals("javascript"))
					lang = "text/javascript";

				ScriptEngine eng = engines.get(lang);
				if (eng == null) {
					if (lang.equals("text/javascript") || lang.equals("js")) {
						/*
						 * If using Javascript, we take advantage of Nashorn directly so a ClassFilter
						 * can be configured to secure scripts when running in a shared environment.
						 * 
						 * TODO A similar solution would be needed for other script engines, or they may
						 * need to be disabled in a shared environment.
						 */
						NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
						eng = factory.getScriptEngine(new WhiteBlackListClassFilter());
					} else {
						eng = manager.getEngineByMimeType(lang);
						if (eng == null)
							eng = manager.getEngineByExtension(lang);
						if (eng == null)
							eng = manager.getEngineByName(lang);
						if (eng == null)
							throw new IllegalArgumentException(
									String.format("No known script engine for type %s", lang));
					}
					engines.put(lang, eng);
					Bindings binding = eng.createBindings();
					binding.put("log", LOG);
					binding.put("processor", this);
					binding.put("attributes", new HashMap<String, Object>(map));
					binding.putAll(this.bindings);
					bindings.put(lang, binding);
				}

				int openEnd = text.indexOf(END_SCRIPT_TAG, openStart);
				if (openEnd != -1) {
					String scriptText = text.substring(openStartEnd + 1, openEnd);
					if (scriptText.startsWith("// <![CDATA[")) {
						scriptText = scriptText.substring(12);
					}
					if (scriptText.endsWith("// ]]")) {
						scriptText = scriptText.substring(0, scriptText.length() - 5);
					}
					Object val = null;
					if (captureScriptErrors) {
						try {
							val = eng.eval(scriptText, bindings.get(lang));
						} catch (Exception e) {
							LOG.error("Embedded cript failed.", e);
							val = "[Error:" + e.getMessage() + "]";
						}
					} else {
						try {
							val = eng.eval(scriptText, bindings.get(lang));
						} catch (ScriptException e) {
							throw new IllegalArgumentException("Failed to evaluate script.", e);
						}
					}
					String str = val == null ? "" : val.toString();
					text = text.substring(0, openStart) + str + text.substring(openEnd + END_SCRIPT_TAG.length());
					startIndx = openEnd + END_SCRIPT_TAG.length();
				} else {
					LOG.warn("Unclosed $(script) tag in message template.");
					startIndx = openStartEnd;
				}
			}
		}
		return text;
	}

	private String processText(String text, Map<String, String> map) {
		final Map<String, String> allVars = new HashMap<>(map);
		buildAllVars(allVars);
		List<Resolver> r = new LinkedList<>(resolvers);
		r.add(new Resolver() {
			@Override
			public String evaluate(String variable) {
				return allVars.get(variable);
			}
		});

		Pattern pattern = Pattern.compile("\\$\\{(.*?)\\}");
		Matcher matcher = pattern.matcher(text);
		StringBuilder builder = new StringBuilder();
		int i = 0;
		while (matcher.find()) {
			String attributeName = matcher.group(1);
			String replacement = null;
			for (Resolver resolver : r) {
				replacement = resolver.evaluate(attributeName);
				if (replacement != null) {
					break;
				}
			}
			builder.append(text.substring(i, matcher.start()));
			if (replacement == null) {
				if (!unknownVariablesAreBlank)
					builder.append(matcher.group(0));
			} else {
				builder.append(replacement);
			}
			i = matcher.end();

		}
		builder.append(text.substring(i, text.length()));
		text = builder.toString();
		return text;
	}

	protected void buildAllVars(Map<String, String> allVars) {
	}
}
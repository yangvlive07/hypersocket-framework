/*******************************************************************************
 * Copyright (c) 2013 LogonBox Limited.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.hypersocket.auth.json;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.hypersocket.auth.AuthenticationRequiredResult;
import com.hypersocket.auth.AuthenticationState;
import com.hypersocket.auth.FallbackAuthenticationRequired;
import com.hypersocket.json.AuthenticationRedirectResult;
import com.hypersocket.json.AuthenticationResult;
import com.hypersocket.json.ResourceStatus;
import com.hypersocket.permissions.AccessDeniedException;
import com.hypersocket.permissions.PermissionService;
import com.hypersocket.permissions.Role;
import com.hypersocket.realm.Realm;
import com.hypersocket.servlet.request.Request;
import com.hypersocket.session.Session;
import com.hypersocket.session.json.SessionTimeoutException;
import com.hypersocket.session.json.SessionUtils;

@Controller
public class LogonController extends AuthenticatedController {

	@Autowired
	PermissionService permissionService; 
	
	@RequestMapping(value = "logon/reset", method = { RequestMethod.GET, RequestMethod.POST}, produces = "application/json")
	@ResponseBody
	@ResponseStatus(value = HttpStatus.OK)
	public AuthenticationResult resetLogon(HttpServletRequest request,
			HttpServletResponse response,
			@RequestParam(required = false) Boolean redirect)
			throws AccessDeniedException, UnauthorizedException, IOException,
			RedirectException {
		AuthenticationState state = AuthenticationState.getCurrentState(Request.get());
		
		String previousScheme = (String) request.getSession().getAttribute(PREVIOUS_AUTHENTICATION_SCHEME);
		if(previousScheme==null) {	
			previousScheme = state == null ? null : state.getScheme().getResourceKey();
		}
		return resetLogon(
				request,
				response,
				previousScheme, 
				redirect);
	}

	@RequestMapping(value = "logon/reset/{scheme}", method = { RequestMethod.GET, RequestMethod.POST}, produces = "application/json")
	@ResponseBody
	@ResponseStatus(value = HttpStatus.OK)
	public AuthenticationResult resetLogon(HttpServletRequest request,
			HttpServletResponse response, @PathVariable String scheme,
			@RequestParam(required = false) Boolean redirect)
			throws AccessDeniedException, UnauthorizedException, IOException,
			RedirectException {
		
		
		
		AuthenticationState.clearCurrentState(request);
		
		AuthenticationResult result = logon(request, response, scheme);
		if (Boolean.TRUE.equals(redirect)) {
			/**
			 * This resets back to zero so the logon UI does not show
			 * any error messages.
			 */
			AuthenticationState state = AuthenticationState.getCurrentState(request);
			if(state!=null) {
				state.clean();
			}
			throw new RedirectException(System.getProperty(
					"hypersocket.uiPath", "/hypersocket/ui"));
		} else {
			return result;
		}

	}
	
	@RequestMapping(value = "logon/clear", method = { RequestMethod.GET}, produces = "application/json")
	@ResponseStatus(value = HttpStatus.OK)
	public void clearLogon(HttpServletRequest request,
			HttpServletResponse response)
			throws AccessDeniedException, UnauthorizedException, IOException,
			RedirectException {
		
		AuthenticationState.clearCurrentState(request);

	}

	public AuthenticationState resetAuthenticationState(
			HttpServletRequest request, HttpServletResponse response,
			String scheme, Realm realm) throws AccessDeniedException, UnsupportedEncodingException {
		AuthenticationState.clearCurrentState(request);
		return AuthenticationState.getOrCreateState(scheme, request,
								realm, null, sessionUtils.getLocale(request));
	}
	
	@RequestMapping(value = "logon/switchRealm/{scheme}/{realm}", method = { RequestMethod.GET,
			RequestMethod.POST }, produces = { "application/json" })
	@ResponseBody
	@ResponseStatus(value = HttpStatus.OK)
	public AuthenticationState switchLogonRealm(
			HttpServletRequest request, HttpServletResponse response,
			@PathVariable String scheme,
			@PathVariable String realm) throws AccessDeniedException, UnsupportedEncodingException {
		realmService.setupSystemContext(); 
		try {
			return resetAuthenticationState(request, response, scheme, realmService.getRealmByName(realm));
		}
		finally {
			realmService.clearPrincipalContext();
		}
	}

	@RequestMapping(value = "logon", method = { RequestMethod.GET,
			RequestMethod.POST }, produces = { "application/json" })
	@ResponseBody
	@ResponseStatus(value = HttpStatus.OK)
	public AuthenticationResult logon(HttpServletRequest request,
			HttpServletResponse response) throws AccessDeniedException,
			UnauthorizedException, IOException, RedirectException {
		return logon(request, response, null);
	}

	@RequestMapping(value = "logon/{scheme}", method = { RequestMethod.GET,
			RequestMethod.POST }, produces = { "application/json" })
	@ResponseBody
	@ResponseStatus(value = HttpStatus.OK)
	public AuthenticationResult logon(HttpServletRequest request,
			HttpServletResponse response, @PathVariable String scheme)
			throws AccessDeniedException, UnauthorizedException, IOException, RedirectException {

		setupSystemContext(realmService.getRealmByHost(request.getServerName()));

		AuthenticationState state = AuthenticationState.getCurrentState(request);

		String flash = (String) request.getSession().getAttribute("flash");
		
		Session session;
		
		boolean requireRedirect = request.getParameterMap().containsKey("rr");
		
		request.getSession().removeAttribute("flash");

		try {
			
			if(state==null) {
				session = sessionUtils.touchSession(request, response);
				if (session != null) {
					try {
						return getSuccessfulResult(session, flash, 
								state!=null ? state.getHomePage() : "",
								request, response);
					} finally {
						clearAuthenticatedContext();
					}
				}
			}
		} catch (AccessDeniedException e) {
			// We are already in login so just continue
		} catch (UnauthorizedException e) {
			// We are already in login so just continue
		} catch (SessionTimeoutException ex) {
			// Previous session has timed out
		}


		try {
			if(state!=null) {
				state.setLastErrorMsg(null);
				state.setLastErrorIsResourceKey(false);
			}
			
			if (state == null
					|| (!StringUtils.isEmpty(scheme) && !state.getInitialSchemeResourceKey().equals(scheme))) {
				// We have not got login state so create
				state = AuthenticationState.createAuthenticationState(scheme, request,
						null, state, sessionUtils.getLocale(request));
			}

			String redirectHome = (String) request.getSession().getAttribute("redirectHome");
			if(redirectHome==null && request.getParameterMap().containsKey("redirectHome")) {
				redirectHome = request.getParameter("redirectHome");
			}
			if(redirectHome!=null) {
				state.setHomePage(redirectHome);
				request.getSession().removeAttribute("redirectHome");
			}

			
			boolean success = authenticationService.logon(state,
					decodeParameters(request.getParameterMap()));
			
			if(state.getSession()!=null) {
				attachSession(state.getSession(), request, response);
			}
						
			if (state.isAuthenticationComplete()
					&& !state.hasPostAuthenticationStep()) {

				// We have authenticated!
				AuthenticationState.clearCurrentState(request);
				
				setupAuthenticatedContext(state.getSession(), sessionUtils.getLocale(request));
				
				List<String> schemes = Arrays.asList(
						configurationService.getValues(state.getRealm(), "session.altHomePage.onSchemes"));
				
				boolean performRedirect =  state.getScheme().supportsHomeRedirect() 
						|| (schemes.contains(state.getScheme().getResourceKey())
								&& !permissionService.hasAdministrativePermission(state.getPrincipal()));
			
				if(requireRedirect && performRedirect && StringUtils.isNotBlank(state.getHomePage())) {
					throw new RedirectException(state.getHomePage());
				}
				
				checkRedirect(request,response);
				 
				
				try {
					return getSuccessfulResult(
							state.getSession(),
							flash,
							performRedirect ? state.getHomePage() : "",
							request, 
							response);
				} finally {
					clearAuthenticatedContext();
				}
			} else {

				checkRedirect(request, response);
				
				return new AuthenticationRequiredResult(
						configurationService.getValue(state.getRealm(),
								"logon.banner"),
						flash!=null ? flash : state.getLastErrorMsg(),
						state.getLastErrorIsResourceKey(),
						!state.isAuthenticationComplete() ? authenticationService
								.nextAuthenticationTemplate(state,
										request.getParameterMap())
								: authenticationService
										.nextPostAuthenticationStep(state),
						false,
//						configurationService.hasUserLocales(), 
						state.isNew(),
						state.getCurrentIndex()==0,
						!state.hasNextStep(), success || state.isNew(),
						state.isAuthenticationComplete(),
						state.getScheme().getLastButtonResourceKey(),
						state.getRealm());
				
			}
		} catch(FallbackAuthenticationRequired e) {
			return resetLogon(request, response, "fallback", false);
		} catch(RedirectException e) {
			/**
			 * This is a hard redirect i.e. the logon method has been called directly by the browser 
			 * not by javascript. If you need to redirect javascript then use JsonRedirectException.
			 */
			throw e;
		} catch(JsonRedirectException e) {
			return new AuthenticationRedirectResult(
					configurationService.getValue(state.getRealm(),
							"logon.banner"),
					flash!=null ? flash : state.getLastErrorMsg(),
					configurationService.hasUserLocales(), e.getMessage());
		} catch(Throwable t) {
			
			if(log.isErrorEnabled()) {
				log.error("Error in authentication flow", t);
			}

			state.setLastErrorMsg(t.getMessage());
			state.setLastErrorIsResourceKey(false);
			
			return new AuthenticationRequiredResult(
					configurationService.getValue(state.getRealm(),
							"logon.banner"),
					state.getLastErrorMsg(),
					state.getLastErrorIsResourceKey(),
					!state.isAuthenticationComplete() ? authenticationService
							.nextAuthenticationTemplate(state,
									request.getParameterMap())
							: authenticationService
									.nextPostAuthenticationStep(state),
					false,
//					configurationService.hasUserLocales(), 
					state.isNew(),
					state.getCurrentIndex()==0,
					!state.hasNextStep(), state.isNew(),
					state.isAuthenticationComplete(),
					state.getScheme().getLastButtonResourceKey(),
					state.getRealm());
		} finally {
			clearAuthenticatedContext();
		}
	}

	protected void checkRedirect(HttpServletRequest request, HttpServletResponse response) throws RedirectException, IOException {
		
		if(request.getParameter("redirectTo")!=null) {
			throw new RedirectException(request.getParameter("redirectTo"));
		}
		if(request.getAttribute("redirectTo")!=null) {
			throw new RedirectException((String)request.getAttribute("redirectTo"));
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Map decodeParameters(Map parameterMap) throws UnsupportedEncodingException {
		
		Map params = new HashMap();
		for(Object key : parameterMap.keySet()) {
			Object val = parameterMap.get(key);
			if(val instanceof String[]) {
				String[] arr = (String[])val;
				if(arr.length == 1) {
					params.put(key, arr[0]);
				} else {
					for(int i=0;i<arr.length;i++) {
						arr[i] = arr[i];
					}
					params.put(key, arr);
				}
			} else if(val instanceof String) {
				params.put(key, (String)val);
			}
		}
		return params;
	}

	@RequestMapping(value = "logoff", method = { RequestMethod.GET,
			RequestMethod.POST }, produces = { "application/json" })
	@ResponseBody
	@ResponseStatus(value = HttpStatus.OK)
	public ResourceStatus<String> logoff(HttpServletRequest request, HttpServletResponse response)
			throws UnauthorizedException, SessionTimeoutException, AccessDeniedException {

		setupAuthenticatedContext(sessionUtils.getSession(request),
				sessionUtils.getLocale(request));

		try {
			Session session = sessionUtils.touchSession(request, response);
			if (session != null && sessionService.isLoggedOn(session, false)) {
				sessionService.closeSession(session);
				request.getSession().removeAttribute(
						SessionUtils.AUTHENTICATED_SESSION);
			}
			
			/**
			 * Logoff will return to default page.
			 */
			return new ResourceStatus<String>("/");
		} finally {
			clearAuthenticatedContext();
		}
	}

	private void attachSession(Session session, HttpServletRequest request, HttpServletResponse response) {
		
		request.getSession().setAttribute(
				SessionUtils.AUTHENTICATED_SESSION, session);

		sessionUtils.addAPISession(request, response,
				session);
	}
	
	@RequestMapping(value = "attach/{authCode}/{sessionId}")
	@ResponseBody
	@ResponseStatus(value = HttpStatus.OK)
	public void attachSession(HttpServletRequest request,
			HttpServletResponse response, @PathVariable String authCode,
			@PathVariable String sessionId, @RequestParam String location)
			throws UnauthorizedException, SessionTimeoutException, RedirectException {

		Session session = sessionService.getSession(sessionId);
		Session authSession = sessionService.getSessionTokenResource(authCode, Session.class);
		
		if(!authSession.equals(session)) {
			throw new UnauthorizedException();
		}
		
		setupAuthenticatedContext(session,
				sessionUtils.getLocale(request));

		try {
			attachSession(authSession, request, response);
			
			if(StringUtils.isEmpty(location)) {
				throw new RedirectException(System.getProperty(
						"hypersocket.uiPath", "/hypersocket/ui"));
			} else {
				throw new RedirectException(location);
			}
		} finally {
			clearAuthenticatedContext();
		}
	}
	
	@RequestMapping(value = "logoff/{id}", method = { RequestMethod.GET,
			RequestMethod.POST }, produces = { "application/json" })
	@ResponseBody
	@ResponseStatus(value = HttpStatus.OK)
	public ResourceStatus<String> logoffSession(HttpServletRequest request,
			HttpServletResponse response, @PathVariable String id)
			throws UnauthorizedException, SessionTimeoutException {

		setupAuthenticatedContext(sessionUtils.getSession(request),
				sessionUtils.getLocale(request));

		try {
			Session session = sessionService.getSession(id);
			if (session != null && sessionService.isLoggedOn(session, false)) {
				sessionService.closeSession(session);
			}
			
			/**
			 * Logoff will return to default page.
			 */
			return new ResourceStatus<String>("/");
		} finally {
			clearAuthenticatedContext();
		}
	}

	private AuthenticationResult getSuccessfulResult(Session session,
			String info, String homePage, 
			HttpServletRequest request, 
			HttpServletResponse response) throws IOException, RedirectException {
		
		return new AuthenticationSuccessResult(info,
				configurationService.hasUserLocales(), session, homePage,
				getCurrentRole(session));
		
	}

	private Role getCurrentRole(Session session) {
		return configurationService.getBooleanValue(session.getCurrentRealm(), "feature.roleSelection") ? session.getCurrentRole() : null;
	}
}

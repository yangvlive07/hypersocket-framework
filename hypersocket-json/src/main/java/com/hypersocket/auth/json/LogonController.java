/*******************************************************************************
 * Copyright (c) 2013 Hypersocket Limited.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.hypersocket.auth.json;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.hypersocket.auth.AuthenticationService;
import com.hypersocket.auth.AuthenticationServiceImpl;
import com.hypersocket.auth.AuthenticationState;
import com.hypersocket.i18n.I18N;
import com.hypersocket.json.AuthenticationRequiredResult;
import com.hypersocket.json.AuthenticationResult;
import com.hypersocket.json.RequestStatus;
import com.hypersocket.permissions.AccessDeniedException;
import com.hypersocket.realm.Realm;
import com.hypersocket.resource.ResourceNotFoundException;
import com.hypersocket.session.Session;
import com.hypersocket.session.json.SessionTimeoutException;
import com.hypersocket.session.json.SessionUtils;

@Controller
public class LogonController extends AuthenticatedController {


	@RequestMapping(value = "logon/reset", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	@ResponseStatus(value = HttpStatus.OK)
	public AuthenticationResult resetLogon(HttpServletRequest request,
			HttpServletResponse response) throws AccessDeniedException,
			UnauthorizedException {
		AuthenticationState state = (AuthenticationState) request.getSession().getAttribute(AUTHENTICATION_STATE_KEY);
		return resetLogon(request, response, state.getScheme().getName());
	}
	
	@RequestMapping(value = "logon/reset/{scheme}", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	@ResponseStatus(value = HttpStatus.OK)
	public AuthenticationResult resetLogon(HttpServletRequest request,
			HttpServletResponse response,
		    @PathVariable String scheme) throws AccessDeniedException,
			UnauthorizedException {
		request.getSession().setAttribute(AUTHENTICATION_STATE_KEY, null);	
		return logon(request, response, scheme);
		
	}
	
	public void resetAuthenticationState(HttpServletRequest request, HttpServletResponse response, String scheme) throws AccessDeniedException {
		request.getSession().setAttribute(AUTHENTICATION_STATE_KEY, null);	
		createAuthenticationState(scheme, request, response);
	}
	
	@RequestMapping(value = "logon", method = { RequestMethod.GET,
			RequestMethod.POST }, produces = { "application/json" })
	@ResponseBody
	@ResponseStatus(value = HttpStatus.OK)
	public AuthenticationResult logon(HttpServletRequest request,
			HttpServletResponse response) throws AccessDeniedException,
			UnauthorizedException {
		return logon(request, response, AuthenticationServiceImpl.DEFAULT_AUTHENTICATION_RESOURCE_KEY);
	}
	
	@RequestMapping(value = "logon/{scheme}", method = { RequestMethod.GET,
			RequestMethod.POST }, produces = { "application/json" })
	@ResponseBody
	@ResponseStatus(value = HttpStatus.OK)
	public AuthenticationResult logon(HttpServletRequest request,
			HttpServletResponse response, @PathVariable String scheme) throws AccessDeniedException,
			UnauthorizedException {

		setupSystemContext();

		try {
			Session session;

			try {
				session = sessionUtils.touchSession(request, response);

				if (session != null) {
					return getSuccessfulResult(session);
				}
			} catch (UnauthorizedException e) {
				// We are already in login so just continue
			} catch (SessionTimeoutException ex) {
				// Previous session has timed out
			}

			AuthenticationState state = (AuthenticationState) request
					.getSession().getAttribute(AUTHENTICATION_STATE_KEY);

			if (state == null) {
				// We have not got login state so create
				state = createAuthenticationState(scheme, request, response);
			} 
			
			authenticationService.logon(state, request.getParameterMap());

			if (state.isAuthenticationComplete()
					&& !state.hasPostAuthenticationStep()) {

				// We have authenticated!
				request.getSession().removeAttribute(AUTHENTICATION_STATE_KEY);
				request.getSession().setAttribute(
						SessionUtils.AUTHENTICATED_SESSION, state.getSession());

				sessionUtils.addAPISession(request, response,
						state.getSession());

				return getSuccessfulResult(state.getSession(),"", state.getHomePage());
			} else {

				return new AuthenticationRequiredResult(
						configurationService.getValue("logon.banner"),
						state.getLastErrorMsg(),
						state.getLastErrorIsResourceKey(),
						!state.isAuthenticationComplete() ? authenticationService
								.nextAuthenticationTemplate(state,
										request.getParameterMap())
								: authenticationService
										.nextPostAuthenticationStep(state),
						i18nService.hasUserLocales(),
						state.isNew(),
						!state.hasNextStep());
			}
		} finally {
			clearSystemContext();

		}

	}

	

	@RequestMapping(value = "logoff")
	@ResponseBody
	@ResponseStatus(value = HttpStatus.OK)
	public void logoff(HttpServletRequest request, HttpServletResponse response)
			throws UnauthorizedException, SessionTimeoutException {

		setupAuthenticatedContext(sessionUtils.getSession(request),
				sessionUtils.getLocale(request));

		try {
			Session session = sessionUtils.touchSession(request, response);
			if (session != null && sessionService.isLoggedOn(session, false)) {
				sessionService.closeSession(session);
				request.getSession().removeAttribute(
						SessionUtils.AUTHENTICATED_SESSION);
			}
		} finally {
			clearAuthenticatedContext();
		}
	}
	
	private AuthenticationResult getSuccessfulResult(Session session,
			String info, String homePage) {
		return new AuthenticationSuccessResult(info,
				i18nService.hasUserLocales(), session, homePage);
	}
	
	private AuthenticationResult getSuccessfulResult(Session session) {
		return new AuthenticationSuccessResult("",
				i18nService.hasUserLocales(), session, "");
	}

}

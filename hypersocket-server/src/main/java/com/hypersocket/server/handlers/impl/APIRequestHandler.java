/*******************************************************************************
 * Copyright (c) 2013 LogonBox Limited.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.hypersocket.server.handlers.impl;

import java.util.List;
import java.util.Objects;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.hypersocket.ApplicationContextServiceImpl;
import com.hypersocket.config.ConfigurationService;
import com.hypersocket.properties.ResourceUtils;
import com.hypersocket.realm.Realm;
import com.hypersocket.server.HypersocketServerImpl;
import com.hypersocket.server.handlers.HttpResponseProcessor;
import com.hypersocket.session.json.SessionUtils;

public class APIRequestHandler extends ServletRequestHandler {

	SessionUtils sessionUtils;
	ConfigurationService configurationService; 
	
	@Override
	public void handleHttpRequest(HttpServletRequest request, HttpServletResponse response,
			HttpResponseProcessor responseProcessor) {
	
		if(Objects.isNull(sessionUtils)) {
			sessionUtils = ApplicationContextServiceImpl.getInstance().getBean(SessionUtils.class);
			configurationService = ApplicationContextServiceImpl.getInstance().getBean(ConfigurationService.class);
		}
		
		Realm currentRealm = sessionUtils.getCurrentRealmOrDefault(request);
		if(configurationService.getBooleanValue(currentRealm, "cors.enabled")) {
		
			List<String> origins = ResourceUtils.explodeCollectionValues(configurationService.getValue(currentRealm, "cors.origins"));
			String requestOrigin = request.getHeader("Origin");

			if(origins.contains(requestOrigin)) {
				/**
				 * Allow CORS to this realm. We must allow credentials as the
				 * API will be useless without them.
				 */
				response.addHeader("Access-Control-Allow-Credentials", "true");
				response.addHeader("Access-Control-Allow-Origin", requestOrigin);
			}
		}
		
		super.handleHttpRequest(request, response, responseProcessor);
	}

	public APIRequestHandler(Servlet servlet,
			int priority) {
		super("api", servlet, priority);
	}
	
	protected void registered() {
		server.addCompressablePath(server.resolvePath(server.getAttribute(
				HypersocketServerImpl.API_PATH, HypersocketServerImpl.API_PATH)));
	}

	@Override
	public boolean handlesRequest(HttpServletRequest request) {
		return request.getRequestURI().startsWith(
				server.resolvePath(server.getAttribute(
						HypersocketServerImpl.API_PATH,
						HypersocketServerImpl.API_PATH)));
	}

	@Override
	public boolean getDisableCache() {
		return false;
	}

}

package com.github.standalone_openig.handler;

import java.util.Map;

import org.forgerock.openig.http.Exchange;

/**
 * Implemente this interface, to customize AuthenticateHandler.
 * set to AuthenticatieHandler.authCallback.
 */
public interface AuthCallback {

	/**
	 * Authenticate user.
	 * return NOT empty Map, if authenticate successful.
	 * 
	 * @param username UserName
	 * @param password Password
	 * @param exchange Exchange.
	 * @param parentHandler AuthenticateHandler
	 * @return NOT empty map when auth succeeded.
	 */
	public Map<String, Object> callback(String username, String password, Exchange exchange,
			AuthenticateHandler parentHandler);
}

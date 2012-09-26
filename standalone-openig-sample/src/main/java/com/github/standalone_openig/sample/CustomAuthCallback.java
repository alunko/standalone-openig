package com.github.standalone_openig.sample;

import java.util.HashMap;
import java.util.Map;

import org.forgerock.openig.http.Exchange;

import com.github.standalone_openig.handler.AuthCallback;
import com.github.standalone_openig.handler.AuthenticateHandler;

/**
 * Sample custom AuthCallback.
 */
public class CustomAuthCallback implements AuthCallback{

	@Override
	public Map<String, Object> callback(String username, String password,
			Exchange exchange, AuthenticateHandler parentHandler) {
		Map<String, Object> userInfo = new HashMap<String, Object>();
		userInfo.put("USERNAME", "TEST_CUSTOM_CALLBACK");
		return userInfo;
	}

}

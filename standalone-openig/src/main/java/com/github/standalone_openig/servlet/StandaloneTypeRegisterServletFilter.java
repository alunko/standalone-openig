package com.github.standalone_openig.servlet;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.forgerock.openig.util.JsonValueUtil;


public class StandaloneTypeRegisterServletFilter implements Filter {

	@SuppressWarnings("unchecked")
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		try {
			//Add to types, Filter, Handler etc
			Field aliasesField = JsonValueUtil.class.getDeclaredField("aliases");
			aliasesField.setAccessible(true);
			Map<String, String> aliases = (Map<String, String>) aliasesField.get(JsonValueUtil.class);
			aliases.put("ChainHandlerServlet", "com.github.standalone_openig.servlet.ChainHandlerServlet");
			aliases.put("AuthenticateHandler", "com.github.standalone_openig.handler.AuthenticateHandler");
			aliases.put("HtmlFileHandler", "com.github.standalone_openig.handler.HtmlFileHandler");
			aliases.put("LoginPageHandler", "com.github.standalone_openig.handler.LoginPageHandler");
			aliases.put("ExpressionValueHeaderFilter", "com.github.standalone_openig.filter.ExpressionValueHeaderFilter");
			aliases.put("LoggerCaptureFilter", "com.github.standalone_openig.filter.LoggerCaptureFilter");
			aliases.put("MultipartClientHandler", "com.github.standalone_openig.handler.MultipartClientHandler");
		} catch (NoSuchFieldException e) {
			//nop
			e.printStackTrace();
		} catch (SecurityException e) {
			//nop
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			//nop
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			//nop
			e.printStackTrace();
		}
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		chain.doFilter( request, response );
	}

	@Override
	public void destroy() {
		//nop
	}

}

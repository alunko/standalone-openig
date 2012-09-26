package com.github.standalone_openig;

import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.config.Config;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapImpl;
import org.forgerock.openig.heap.HeapUtil;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.ConsoleLogSink;
import org.forgerock.openig.resource.ResourceException;

import com.github.standalone_openig.config.WebinfConfigResource;


/**
 * Locate config.json in /WEB-INF/config.json.
 */
public class WebinfConfigGatewayServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private HttpServlet servlet;

	@Override
	public void init() throws ServletException {
		try {
			ServletContext context = getServletConfig().getServletContext();
			JsonValue config = new Config(new WebinfConfigResource("ForgeRock",
					"OpenIG", context)).read();
			HeapImpl heap = new HeapImpl();
			heap.put("ServletContext", context);
			heap.put("TemporaryStorage", new TemporaryStorage());
			heap.put("LogSink", new ConsoleLogSink());
			heap.init(config.get("heap").required().expect(Map.class));
			servlet = HeapUtil.getRequiredObject(heap, config.get(
					"servletObject").required(), HttpServlet.class);
		} catch (HeapException he) {
			throw new ServletException(he);
		} catch (JsonValueException jve) {
			throw new ServletException(jve);
		} catch (ResourceException re) {
			throw new ServletException(re);
		}
	}

	@Override
	public void service(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		
		servlet.service(request, response);
	}
}
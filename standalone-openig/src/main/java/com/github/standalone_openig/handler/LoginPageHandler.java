package com.github.standalone_openig.handler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.handler.GenericHandler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.HttpUtil;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.log.LogTimer;

/**
 * Local Login Page Render Handler.
 */
public class LoginPageHandler extends GenericHandler {
	/** Local login page filename */
	public String loginPage = "/login.html";
	/** login error message */
	public String errorMessage = "Login Failed.";
	
	public String charset = System.getProperty("file.encoding");

	@Override
	public void handle(Exchange exchange) throws HandlerException, IOException {
		LogTimer timer = logger.getTimer().start();

		HttpServletRequest req = (HttpServletRequest) exchange.get(HttpServletRequest.class.getName());
		String actionPath = req.getRequestURI();
		boolean isLoginFailure = (req.getAttribute(AuthenticateHandler.REQUEST_LOGIN_FAILURE_KEY) != null);

		String targetFile = loginPage;
		
		String pageContent = loadFile(targetFile, isLoginFailure, exchange);
		Response response = new Response();
		response.status = 200;
		response.headers.remove("Content-Type");
		response.headers.add("Content-Type", "text/html");
		HttpUtil.toEntity(response, pageContent, Charset.forName(charset));
		System.out.println(response.headers.getFirst("Content-Type"));
		exchange.response = response; // finally replace response in the
										// exchange
		timer.stop();
	}

	protected String loadFile(String targetFile, boolean isLoginFailure, Exchange exchange) throws IOException {
		HttpServletRequest req = (HttpServletRequest) exchange.get(HttpServletRequest.class.getName());
		ServletContext sc = req.getSession().getServletContext();
		
		
		InputStream targetFileIs = sc.getResourceAsStream(targetFile);
		if(targetFileIs == null){
			File targetFileObj = new File(targetFile);
			if(targetFileObj.exists()){
				targetFileIs = new FileInputStream(targetFileObj);
			}
			if(targetFileIs == null){
				throw new IllegalStateException("File Not Found in WebApp or FileSystem. file=" + targetFile);
			}
		}
		StringBuilder loginPageContent = inputStreemToStringBuilder(targetFileIs);
		
		String actionPath = req.getRequestURI();
		String actionQueryString = req.getQueryString();
		if(actionQueryString != null && !"".equals(actionQueryString)){
			actionPath += "?" + actionQueryString;
		}
		
		replaceStringBuilder(loginPageContent, "${action}", actionPath);
		if(isLoginFailure){
			//embed error message
			replaceStringBuilder(loginPageContent, "${errorMessage}", errorMessage);
		}
		else{
			//clear error message
			replaceStringBuilder(loginPageContent, "${errorMessage}", "");
		}
		return loginPageContent.toString();
	}
	
	private void replaceStringBuilder(StringBuilder src, String from, String to){
		int pos = src.indexOf(from);
		if(pos < 0){
			return;
		}
		src.replace(pos, pos + from.length(), to);
	}

	private StringBuilder inputStreemToStringBuilder(InputStream in) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(in,
				charset));
		StringBuilder buf = new StringBuilder();
		String str;
		while ((str = reader.readLine()) != null) {
			buf.append(str);
			buf.append("\n");
		}
		reader.close();
		in.close();
		return buf;
	}

	/**
	 * Creates and initializes a static attribute provider in a heap
	 * environment.
	 */
	public static class Heaplet extends NestedHeaplet {
		@Override
		public Object create() throws HeapException, JsonValueException {
			LoginPageHandler handler = new LoginPageHandler();
			String loginPage = config.get("loginPage").asString();
			if (loginPage != null && loginPage != "") {
				handler.loginPage = loginPage;
			}
			String errorMessage = config.get("errorMessage").asString();
			if (errorMessage != null && errorMessage != "") {
				handler.errorMessage = errorMessage;
			}
			String charset = config.get("charset").asString();
			if (charset != null && charset != "") {
				handler.charset = charset;
			}
			return handler;
		}
	}

}
package com.github.standalone_openig.handler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

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
 * Local HTML Render Handler.
 * Local HTML file rendered, that requested url path is matched local filename.
 */
public class HtmlFileHandler extends GenericHandler {
	
	/** specific Local HTML File */
	public String file = null;
	public String charset = System.getProperty("file.encoding");

	@Override
	public void handle(Exchange exchange) throws HandlerException, IOException {
		LogTimer timer = logger.getTimer().start();

		HttpServletRequest req = (HttpServletRequest) exchange.get(HttpServletRequest.class.getName());
		
		ServletContext sc = req.getSession().getServletContext();
		
		String targetFile = file;
		if(targetFile == null){
			//not specify local filename, request path assigned
			if(req.getRequestURI().startsWith("/WEB-INF")){
				throw new IllegalStateException("Cannot access under /WEB-INF Resource.");
			}
			targetFile = req.getRequestURI();
		}
		if("/".equals(targetFile)){
			//default html file
			if(sc.getResourceAsStream("index.htm") != null){
				targetFile = "/index.htm";
			}
			else{
				targetFile = "/index.html";
			}
		}

		String pageContent = loadFile(targetFile, exchange);
		Response response = new Response();
		response.status = 200;
		HttpUtil.toEntity(response, pageContent.toString(), null);
		exchange.response = response;
		timer.stop();
	}
	
	protected String loadFile(String targetFile, Exchange exchange) throws IOException {
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
		return inputStreemToStringBuilder(targetFileIs).toString();
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

	public static class Heaplet extends NestedHeaplet {
		@Override
		public Object create() throws HeapException, JsonValueException {
			HtmlFileHandler handler = new HtmlFileHandler();
			String file = config.get("file").asString();
			if (file != null && file != "") {
				handler.file = file;
			}
			String charset = config.get("charset").asString();
			if (charset != null && charset != "") {
				handler.charset = charset;
			}
			return handler;
		}
	}

}
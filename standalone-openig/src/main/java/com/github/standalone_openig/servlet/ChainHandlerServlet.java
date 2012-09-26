package com.github.standalone_openig.servlet;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.filter.Filter;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapUtil;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.io.BranchingInputStream;
import org.forgerock.openig.io.BranchingStreamWrapper;
import org.forgerock.openig.io.Streamer;
import org.forgerock.openig.io.TemporaryStorage;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.log.Logger;
import org.forgerock.openig.servlet.GenericServletHeaplet;
import org.forgerock.openig.servlet.ServletSession;
import org.forgerock.openig.util.CaseInsensitiveSet;
import org.forgerock.openig.util.URIUtil;

/**
 * HandlerServlet with filter-chain.
 */
public class ChainHandlerServlet extends HttpServlet {

 private static final long serialVersionUID = 1L;
 
 /** List of filter. */
 public final List<Filter> filters = new ArrayList<Filter>();

 /** same Handler Servlet */
 protected URI baseURI;

 /** same Handler Servlet */
 protected Handler handler;

 /** same Handler Servlet */
 protected TemporaryStorage storage;

 /** same Handler Servlet */
 protected Logger logger;

 /** Methods that should not include an entity body. */
 private static final CaseInsensitiveSet NON_ENTITY_METHODS =
  new CaseInsensitiveSet(Arrays.asList("GET", "HEAD", "TRACE", "DELETE"));

 /**
  * Handles a servlet request by dispatching it to a handler. It receives a servlet request,
  * translates it into an exchange object, dispatches the exchange to a handler, then
  * translates the exchange response into an servlet response.
  */
 @Override
 @SuppressWarnings("unchecked")
 public void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
     LogTimer timer = logger.getTimer().start();
     Exchange exchange = new Exchange();
     // populate request
     exchange.request = new Request();
     exchange.request.method = request.getMethod();
     try {
         exchange.request.uri = URIUtil.create(request.getScheme(), null, request.getServerName(),
          request.getServerPort(), request.getRequestURI(), request.getQueryString(), null);
         if (baseURI != null) {
             exchange.request.uri = URIUtil.rebase(exchange.request.uri, baseURI);
         }
     } catch (URISyntaxException use) {
         throw new ServletException(use);
     }
     // request headers
     for (Enumeration<?> e = request.getHeaderNames(); e.hasMoreElements();) {
         String name = (String)e.nextElement();
         exchange.request.headers.addAll(name, Collections.list(request.getHeaders(name)));
     }
     // include request entity if appears to be provided with request
     if ((request.getContentLength() > 0 || request.getHeader("Transfer-Encoding") != null)
     && !NON_ENTITY_METHODS.contains(exchange.request.method)) {
         exchange.request.entity = new BranchingStreamWrapper(request.getInputStream(), storage);
     }
     // remember request entity so that it (and its children) can be properly closed
     BranchingInputStream requestEntityTrunk = exchange.request.entity;
     exchange.session = new ServletSession(request);
     exchange.principal = request.getUserPrincipal();
     // handy servlet-specific attributes, sure to be abused by downstream filters
     exchange.put("javax.servlet.http.HttpServletRequest", request);
     exchange.put("javax.servlet.http.HttpServletResponse", response);
     try {
         // filter chain and handle request
         try {
        	 
        	 new Handler() {
                 private int cursor = 0;
                 public void handle(Exchange exchange) throws HandlerException, IOException {
                     int saved = cursor; // save position to restore after the call
                     try {
                         if (cursor < filters.size()) {
                             filters.get(cursor++).filter(exchange, this);
                         } else {
                             handler.handle(exchange);
                         }
                     } finally {
                         cursor = saved;
                     }
                 }
             }.handle(exchange);
             
         } catch (HandlerException he) {
             throw new ServletException(he);
         }
         // response status-code (reason-phrase deprecated in Servlet API)
         response.setStatus(exchange.response.status);
         // response headers
         for (String name : exchange.response.headers.keySet()) {
             for (String value : exchange.response.headers.get(name)) {
                 if (value != null && value.length() > 0) {
                     response.addHeader(name, value);
                 }
             }
         }
         // response entity (if applicable)
         if (exchange.response.entity != null) {
             OutputStream out = response.getOutputStream();
             Streamer.stream(exchange.response.entity, out);
             out.flush();
         }
     } finally { // final cleanup
         if (requestEntityTrunk != null) {
             try {
                 requestEntityTrunk.close();
             } catch (IOException ioe) {
                 // ignore exception closing a stream
             }
         }
         if (exchange.response != null && exchange.response.entity != null) {
             try {
                 exchange.response.entity.close(); // important!
             } catch (IOException ioe) {
                 // ignore exception closing a stream
             }
         }
     }        
     timer.stop();
 }

 public static class Heaplet extends GenericServletHeaplet {
     @Override public HttpServlet createServlet() throws HeapException, JsonValueException {
    	 ChainHandlerServlet servlet = new ChainHandlerServlet();
         servlet.handler = HeapUtil.getRequiredObject(heap, config.get("handler").required(), Handler.class);
         servlet.baseURI = config.get("baseURI").asURI(); // optional
         servlet.storage = this.storage;
         servlet.logger = this.logger;
         
         for (JsonValue filter : config.get("filters").required().expect(List.class)) {
        	 servlet.filters.add(HeapUtil.getRequiredObject(heap, filter, Filter.class));
         }
         
         return servlet;
     }
 }
}

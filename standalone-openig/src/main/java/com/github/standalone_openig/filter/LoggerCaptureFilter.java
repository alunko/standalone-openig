package com.github.standalone_openig.filter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.filter.CaptureFilter;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.header.ContentTypeHeader;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.HttpUtil;
import org.forgerock.openig.http.Message;
import org.forgerock.openig.http.Request;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.io.Streamer;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.util.JsonValueUtil;

/**
 * CaptureFilter for Commons-Logging 
 */
public class LoggerCaptureFilter extends CaptureFilter {

	/** Set of common textual content with non-text content-types to capture. */
	private static final HashSet<String> TEXT_TYPES = new HashSet<String>(
			Arrays.asList("application/atom+xml", "application/javascript",
					"application/json", "application/rss+xml",
					"application/xhtml+xml", "application/xml",
					"application/xml-dtd", "application/x-www-form-urlencoded")); // make
																					// all
																					// entries
																					// lower
																					// case
	/**
	 * Condition to evaluate to determine whether to capture an exchange
	 * (default: {@code null} a.k.a.&nbspunconditional).
	 */
	public Expression condition = null;

	/** Indicates message entity should be captured (default: {@code true}). */
	public boolean captureEntity = true;

	/** Name of this capture filter instance. */
	public String instance = getClass().getSimpleName();

	/** Used to assign each exchange a monotonically increasing number. */
	private AtomicLong sequence = new AtomicLong(0L);

	Log outputLogger = LogFactory.getLog(LoggerCaptureFilter.class);
	
	/**
	 * Filters the exchange by capturing request and response messages.
	 */
	@Override
	public synchronized void filter(Exchange exchange, Handler next)
			throws HandlerException, IOException {
		LogTimer timer = logger.getTimer().start();
		
		Object eval = (condition != null ? condition.eval(exchange)
				: Boolean.TRUE);
		if (eval instanceof Boolean && (Boolean) eval) {
			long id = sequence.incrementAndGet();
			captureRequest(exchange.request, id);
			next.handle(exchange);
			captureResponse(exchange.response, id);
		}
		timer.stop();
	}

	private void captureRequest(Request request, long id) throws IOException {
		StringWriter sw = new StringWriter();
		PrintWriter writer = new PrintWriter(sw);
		writer.println();
		writer.println("--- REQUEST " + id + " --->");
		writer.println();
		writer.println(request.method + " " + request.uri + " "
				+ request.version);
		writeHeaders(request, writer);
		writeEntity(request, writer);
		writer.flush();
		outputLogger.debug(sw.toString());
		writer.close();
		sw.close();
	}

	private void captureResponse(Response response, long id) throws IOException {
		StringWriter sw = new StringWriter();
		PrintWriter writer = new PrintWriter(sw);
		writer.println();
		writer.println("<--- RESPONSE " + id + " ---");
		writer.println();
		writer.println(response.version + " " + response.status + " "
				+ response.reason);
		writeHeaders(response, writer);
		writeEntity(response, writer);
		writer.flush();
		outputLogger.debug(sw.toString());
		writer.close();
		sw.close();
	}

	private void writeHeaders(Message message, PrintWriter writer) throws IOException {
		for (String key : message.headers.keySet()) {
			for (String value : message.headers.get(key)) {
				writer.println(key + ": " + value);
			}
		}
	}

	private void writeEntity(Message message, PrintWriter writer) throws IOException {
		ContentTypeHeader contentType = new ContentTypeHeader(message);
		if (message.entity == null || contentType.type == null) {
			return;
		}
		writer.println();
		if (!captureEntity) { // simply show presence of an entity
			writer.println("[entity]");
			return;
		}
		String type = (contentType.type != null ? contentType.type
				.toLowerCase() : null);
		if (!(contentType.charset != null || (type != null && // text or
																// whitelisted
																// type
		(TEXT_TYPES.contains(type) || type.startsWith("text/"))))) {
			writer.println("[binary entity]");
			return;
		}
		try {
			Reader reader = HttpUtil.entityReader(message, true, null);
			try {
				Streamer.stream(reader, writer);
			} finally {
				try {
					reader.close();
				} catch (IOException ioe) {
					// suppress this exception
				}
			}
		} catch (UnsupportedEncodingException uee) {
			writer.println("[entity contains data in unsupported encoding]");
		} catch (UnsupportedCharsetException uce) {
			writer.println("[entity contains characters in unsupported character set]");
		} catch (IllegalCharsetNameException icne) {
			writer.println("[entity contains characters in illegal character set]");
		}
		writer.println(); // entity may not terminate with new line, so here it
							// is
	}

	/** Creates and initializes a capture filter in a heap environment. */
	public static class Heaplet extends NestedHeaplet {
		@Override
		public Object create() throws HeapException, JsonValueException {
			LoggerCaptureFilter filter = new LoggerCaptureFilter();
			filter.condition = JsonValueUtil.asExpression(config
					.get("condition")); // optional
			filter.captureEntity = config.get("captureEntity")
					.defaultTo(filter.captureEntity).asBoolean(); // optional
			filter.instance = super.name;
			return filter;
		}
	}
}
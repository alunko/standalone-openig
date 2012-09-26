package com.github.standalone_openig.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.filter.GenericFilter;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Headers;
import org.forgerock.openig.http.Message;
import org.forgerock.openig.http.MessageType;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.util.CaseInsensitiveSet;
import org.forgerock.openig.util.JsonValueUtil;

public class ExpressionValueHeaderFilter extends GenericFilter {

	/** Indicates the type of message in the exchange to filter headers for. */
	MessageType messageType;

	/** The names of header fields to remove from the message. */
	public final CaseInsensitiveSet remove = new CaseInsensitiveSet();

	/** Header fields to add to the message. */
//	public final Headers add = new Headers();
	
	public final Map<String, List<Expression>> headerExpressionMap = new LinkedHashMap<String, List<Expression>>();

	/**
	 * Removes all specified headers, then adds all specified headers.
	 * 
	 * @param message
	 *            the message to remove headers from and add headers to.
	 */
	private void process(Message message, Exchange exchange) {
		for (String s : this.remove) {
			message.headers.remove(s);
		}
		Headers addHeaders = new Headers();
		for(Map.Entry<String, List<Expression>> entry : headerExpressionMap.entrySet()) {
			List<Expression> values = entry.getValue();
			List<String> headerValues = new ArrayList<String>(values.size());
			for(Expression expression : values){
				headerValues.add(expression.eval(exchange, String.class));
			}
			addHeaders.addAll(entry.getKey(), headerValues);
		}
		message.headers.addAll(addHeaders);
	}

	/**
	 * Filters the request and/or response of an exchange by removing headers
	 * from and adding headers to a message.
	 */
	@Override
	public void filter(Exchange exchange, Handler next) throws HandlerException, IOException {
		LogTimer timer = logger.getTimer().start();
		if (messageType == MessageType.REQUEST) {
			process(exchange.request, exchange);
		}
		next.handle(exchange);
		if (messageType == MessageType.RESPONSE) {
			process(exchange.response, exchange);
		}
		timer.stop();
	}

	/** Creates and initializes a header filter in a heap environment. */
	public static class Heaplet extends NestedHeaplet {
		@Override
		public Object create() throws HeapException, JsonValueException {
			ExpressionValueHeaderFilter filter = new ExpressionValueHeaderFilter();
			filter.messageType = config.get("messageType").required()
					.asEnum(MessageType.class); // required
			filter.remove.addAll(config.get("remove")
					.defaultTo(Collections.emptyList()).asList(String.class)); // optional
			JsonValue add = config.get("add").defaultTo(Collections.emptyMap())
					.expect(Map.class); // optional
			for (String key : add.keys()) {
				List<Expression> valuesExpressionList = new ArrayList<Expression>();
				for (JsonValue valueJsonValue : add.get(key).required()
						.expect(List.class)) {
					valuesExpressionList.add(JsonValueUtil.asExpression(valueJsonValue));
				}
				filter.headerExpressionMap.put(key, valuesExpressionList);
			}
			return filter;
		}
	}
}

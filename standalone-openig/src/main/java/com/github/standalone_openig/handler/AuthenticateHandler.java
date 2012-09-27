package com.github.standalone_openig.handler;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.openig.el.Expression;
import org.forgerock.openig.handler.GenericHandler;
import org.forgerock.openig.handler.Handler;
import org.forgerock.openig.handler.HandlerException;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.HeapUtil;
import org.forgerock.openig.heap.NestedHeaplet;
import org.forgerock.openig.http.Exchange;
import org.forgerock.openig.http.Response;
import org.forgerock.openig.log.LogLevel;
import org.forgerock.openig.log.LogTimer;
import org.forgerock.openig.util.JsonValueUtil;
import org.forgerock.util.Factory;
import org.forgerock.util.LazyMap;

import com.github.standalone_openig.internal.ExpressionExtractor;

/**
 * Authentication Handler
 */
@SuppressWarnings({"unchecked", "unused"})
public class AuthenticateHandler extends GenericHandler  {
	public DataSource dataSource;
	public String authPreparedStatement;
	public List<Expression> authParameters = new ArrayList<Expression>();
	public Expression target;
	public Handler successHandler;
	public Handler failureHandler;
	public Expression username;
	public Expression password;
	public String logoutPath = "/logout";
	public String authCallback;
	
	/** specified callback on authenticate. */
	public AuthCallback callback;

	/** UserInfo session-key */
	public static final String SESSION_USER_INFO_KEY = AuthenticateHandler.class.getName() + "#USER_INFO_SESSION_KEY";
	/** Login Failure Flag httpservlerrequest-key. use with LoginPageHandler. */
	public static final String REQUEST_LOGIN_FAILURE_KEY = AuthenticateHandler.class.getName() + "#LOGIN_FAILURE_KEY";

	/** authParameters. special char Username. evaluate parameter 'username'. */
	public static final String PARAMETER_RESERVED_USERNAME = "${username}";
	/** authParameters. special char Password. evaluate parameter 'password'. */
	public static final String PARAMETER_RESERVED_PASSWORD = "${password}";
	
	/**
	 * authenticate execute
	 * @param exchange Exchange
	 */
	public void handle(Exchange exchange) throws HandlerException, IOException {
		LogTimer timer = logger.getTimer().start();
		
		HttpServletRequest req = (HttpServletRequest) exchange.get(HttpServletRequest.class.getName());
		HttpServletResponse res = (HttpServletResponse) exchange.get(HttpServletResponse.class.getName());
		HttpSession session = req.getSession();
		if(req.getRequestURI().equals(logoutPath)){
			//logout process
			session.invalidate();
			//Redirect to root
			res.sendRedirect("/");
			Response response = new Response();
			response.status = 307;
			exchange.response = response;
			return;
		}

		Map<String, Object> userInfo = (Map<String, Object>) session.getAttribute(AuthenticateHandler.SESSION_USER_INFO_KEY);
		
		String uname = null;
		if(username != null){
			uname = username.eval(exchange, String.class);
		}
		String pass = null;
		if(password != null){
			pass = password.eval(exchange, String.class);
		}
		boolean isAutheiticateExecuted = false;
		
		if(userInfo == null && uname != null && !"".equals(uname)){
			// Session NOT exist
			if(authCallback != null && !"".equals(authCallback)){
				//Specify authCallback.
				try {
					if(this.callback == null){
						
						Class<AuthCallback> callbackClass = (Class<AuthCallback>) 
								Thread.currentThread().getContextClassLoader().loadClass(authCallback);
						this.callback = callbackClass.newInstance();
					}
					userInfo = callback.callback(uname, pass, exchange, this);
				} catch (ClassNotFoundException e) {
					throw new IllegalStateException("Cannot found authCallback class.authCallback=" + authCallback, e);
				} catch (IllegalAccessException e) {
					throw new IllegalStateException("AuthCallback instance cannot create. authCallback=" + authCallback, e);
				} catch (InstantiationException e) {
					throw new IllegalStateException("AuthCallback instance cannot create. authCallback=" + authCallback, e);
				}
			}
			else{
				//default authenticate with DB
				userInfo = authenticate(exchange, uname, pass);
			}
			isAutheiticateExecuted = true;
		}
		
		if(userInfo == null || userInfo.isEmpty()){
			//authenticate failed
			if(isAutheiticateExecuted){
				req.setAttribute(AuthenticateHandler.REQUEST_LOGIN_FAILURE_KEY, Boolean.TRUE);
			}
		    failureHandler.handle(exchange);
		}
		else{
			//authenticate successful.
			session.setAttribute(AuthenticateHandler.SESSION_USER_INFO_KEY, userInfo);

			final Map<String, Object> authenticatedResult = userInfo;
			target.set(exchange, new LazyMap<String, Object>(
					new Factory<Map<String, Object>>() {
						public Map<String, Object> newInstance() {
							return authenticatedResult;
						}
					}));
			if(isAutheiticateExecuted){
				//Redirect to clear login parameters
				res.sendRedirect(req.getRequestURI());
				Response response = new Response();
				response.status = 307;
				exchange.response = response;
			}
			else{
				successHandler.handle(exchange);
			}
		}
		
		timer.stop();
	}

	private void replaceStringBuilder(StringBuilder src, String from, String to){
		int pos = src.indexOf(from);
		if(pos < 0){
			return;
		}
		src.replace(pos, pos + from.length(), to);
	}
	
	public Map<String, Object> authenticate(final Exchange exchange, String uname, String pass){

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		Connection c = null;
		try {
			c = dataSource.getConnection();
			PreparedStatement ps = c
					.prepareStatement(authPreparedStatement); // probably
															// cached
															// in
															// connection
															// pool
			ps.clearParameters(); // probably unnecessary but a
									// safety precaution
			Object[] p = new Object[authParameters.size()];
			for (int n = 0; n < p.length; n++) {
				Expression expr = authParameters.get(n);
				String exprString = ExpressionExtractor.extractExpression(expr).getExpressionString();
				
				if(PARAMETER_RESERVED_USERNAME.equals(exprString)){
					if (uname != null) {
						p[n] = uname;
					} else {
						p[n] = "";
					}
				}
				else if(PARAMETER_RESERVED_PASSWORD.equals(exprString)){
					if (pass != null) {
						p[n] = pass;
					} else {
						p[n] = "";
					}
				}
				else{
					p[n] = expr.eval(exchange);
				}
			}
			for (int n = 0; n < p.length; n++) {
				ps.setObject(n + 1, p[n]);
			}
			if (logger.isLoggable(LogLevel.DEBUG)) {
				logger.debug("Query: " + authPreparedStatement
						+ ": " + Arrays.toString(p));
			}
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				ResultSetMetaData rsmd = rs.getMetaData();
				int columns = rsmd.getColumnCount();
				for (int n = 1; n <= columns; n++) {
					result.put(rsmd.getColumnLabel(n), rs
							.getObject(n));
				}
			}
			else{
				//NO RESULT
				logger.debug("SQL NO RESULT. Authenticated Failed.");
				return null;
			}
			if (logger.isLoggable(LogLevel.DEBUG)) {
				StringBuilder sb = new StringBuilder(
						"Result: { ");
				for (Iterator<String> i = result.keySet()
						.iterator(); i.hasNext();) {
					String key = i.next();
					sb.append(key).append(": ").append(
							result.get(key));
					if (i.hasNext()) {
						sb.append(", ");
					}
				}
				sb.append(" }");
				logger.debug(sb.toString());
			}
			rs.close();
			ps.close();
		} catch (SQLException sqle) {
			logger.warning(sqle); // probably a config issue
		} finally {
			if (c != null) {
				try {
					c.close();
				} catch (SQLException sqle) {
					logger.warning(sqle); // probably a network
											// issue
				}
			}
		}
		return result;
	}

	/**
	 * Creates and initializes a static attribute provider in a heap
	 * environment.
	 */
	public static class Heaplet extends NestedHeaplet {
		@Override
		public Object create() throws HeapException, JsonValueException {
			AuthenticateHandler handler = new AuthenticateHandler();
			handler.target = JsonValueUtil.asExpression(config.get("target")
					.required());
			InitialContext ctx;
			try {
				ctx = new InitialContext();
			} catch (NamingException ne) {
				throw new HeapException(ne);
			}
			String dataSourceStr = config.get("dataSource").asString();
			if(dataSourceStr != null && !"".equals(dataSourceStr)){
				try {
					handler.dataSource = (DataSource) ctx.lookup(dataSourceStr);
				} catch (NamingException ne) {
					throw new JsonValueException(config.get("dataSource"), ne);
				} catch (ClassCastException ne) {
					throw new JsonValueException(config.get("dataSource"), "expecting "
							+ DataSource.class.getName() + " type");
				}
			}
			handler.authPreparedStatement = config.get("authPreparedStatement")
					.asString();
			
			JsonValue authParametersListJV = config.get("authParameters").expect(List.class);
			if(authParametersListJV != null){
				for (JsonValue parameter : authParametersListJV) {
					handler.authParameters
							.add(JsonValueUtil.asExpression(parameter));
				}
			}
			
			String authCallback = config.get("authCallback").asString();
			if(authCallback != null && authCallback != ""){
				handler.authCallback = authCallback;
			}
			handler.successHandler = HeapUtil.getRequiredObject(heap, config.get("successHandler"), Handler.class);
			handler.failureHandler = HeapUtil.getRequiredObject(heap, config.get("failureHandler"), Handler.class);
			handler.username = JsonValueUtil.asExpression(config.get("username"));
			handler.password = JsonValueUtil.asExpression(config.get("password"));
			String logoutPath = config.get("logoutPath").asString();
			if(logoutPath != null && !"".equals(logoutPath)){
				handler.logoutPath = logoutPath;
			}
			return handler;
		}
	}

}
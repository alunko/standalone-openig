package com.github.standalone_openig.internal;

import java.lang.reflect.Field;

import org.forgerock.openig.el.Expression;

import de.odysseus.el.TreeValueExpression;

public class ExpressionExtractor {
	
	/**
	 * extract TreeValueExpression object from Expression
	 * @param expr Expression
	 * @return TreeValueExpression
	 */
	public static TreeValueExpression extractExpression(Expression expr){
		try {
			Field f = Expression.class.getDeclaredField("valueExpression");
			f.setAccessible(true);
			return (TreeValueExpression) f.get(expr);
		} catch (NoSuchFieldException e) {
			throw new IllegalStateException("Cannot extract valueExpression ", e);
		} catch (SecurityException e) {
			throw new IllegalStateException("Cannot extract valueExpression", e);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Cannot extract valueExpression", e);
		}
	}

}

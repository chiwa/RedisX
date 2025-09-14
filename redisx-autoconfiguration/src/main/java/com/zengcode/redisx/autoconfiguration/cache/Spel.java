package com.zengcode.redisx.autoconfiguration.cache;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

public final class Spel {
    private static final ExpressionParser P = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer N = new DefaultParameterNameDiscoverer();

    public static String evalStr(String expr, Method m, Object[] args) {
        if (expr == null || expr.isBlank()) return null;
        var c = ctx(m, args, null);
        var v = P.parseExpression(expr).getValue(c);
        return v == null ? null : String.valueOf(v);
    }

    public static boolean evalBool(String expr, Method m, Object[] args, boolean defaultIfBlank) {
        if (expr == null || expr.isBlank()) return defaultIfBlank;
        var c = ctx(m, args, null);
        var v = P.parseExpression(expr).getValue(c);
        return toBool(v);
    }

    public static boolean evalBoolWithResult(String expr, Method m, Object[] args, Object result, boolean defaultIfBlank) {
        if (expr == null || expr.isBlank()) return defaultIfBlank;
        var c = ctx(m, args, result);
        var v = P.parseExpression(expr).getValue(c);
        return toBool(v);
    }

    private static StandardEvaluationContext ctx(Method m, Object[] args, Object result) {
        var c = new StandardEvaluationContext();
        var names = N.getParameterNames(m);
        if (names != null) for (int i = 0; i < names.length; i++) c.setVariable(names[i], args[i]);
        if (result != null) c.setVariable("result", result);
        return c;
    }

    private static boolean toBool(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v));
    }
}

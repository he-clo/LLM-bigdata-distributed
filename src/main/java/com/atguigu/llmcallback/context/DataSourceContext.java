package com.atguigu.llmcallback.context;

public final class DataSourceContext {

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    public static void set(String ds) {
        CONTEXT.set(ds);
    }

    public static String get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static final String MASTER = "master";
    public static final String SLAVE  = "slave";
}
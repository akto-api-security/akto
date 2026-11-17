package com.akto.testing.scripts;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/**
 * Builds the Nashorn engine a script runs in. Library scripts get the plain engine, as before.
 * Validation drafts (Ask Akto written, so prompt-injectable through the sample traffic they were
 * written from) get an engine whose ClassFilter only exposes the classes HTTP polling scripts need:
 * no System, Runtime, files, class loading or reflection.
 *
 * Not closed by the filter: java.net.URL still opens file: URLs and reaches internal hosts. The SaaS
 * script-write gate (TestScriptService.requireWriteAccess) is the control for that; see
 * service/scripts/validation/CLAUDE.md.
 */
public final class ScriptEngines {

    // Exact class names; Nashorn asks the filter for every Java class a script names.
    public static final Set<String> DRAFT_ALLOWED_CLASSES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "java.net.URL",
        "java.net.URLEncoder",
        "java.net.URLDecoder",
        "java.net.HttpURLConnection",
        "java.net.SocketTimeoutException",
        "java.io.BufferedReader",
        "java.io.InputStreamReader",
        "java.io.BufferedWriter",
        "java.io.OutputStreamWriter",
        "java.io.ByteArrayOutputStream",
        "java.lang.String",
        "java.lang.StringBuilder",
        "java.lang.Thread",
        "java.util.UUID",
        "java.util.Map",
        "java.util.HashMap",
        "java.util.LinkedHashMap",
        "java.util.List",
        "java.util.ArrayList",
        "java.util.Collection",
        "java.util.Iterator",
        "java.util.Base64",
        "java.nio.charset.StandardCharsets",
        "java.security.MessageDigest",
        "javax.crypto.Mac",
        "javax.crypto.spec.SecretKeySpec"
    )));

    // Standalone Nashorn (JDK 15+, the dashboard runs 17) first, then the JDK 8 built-in one. The
    // standalone jar is runtime-scoped (libs/utils pom), so neither can be referenced at compile time.
    private static final String[] NASHORN_API_PACKAGES = {
        "org.openjdk.nashorn.api.scripting",
        "jdk.nashorn.api.scripting"
    };

    private ScriptEngines() {}

    public static ScriptEngine create(boolean sandboxed) {
        if (!sandboxed) {
            return new ScriptEngineManager().getEngineByName("nashorn");
        }
        return createSandboxed();
    }

    /** Never falls back to the plain engine: if no filtered engine can be built, the script does not run. */
    private static ScriptEngine createSandboxed() {
        for (String pkg : NASHORN_API_PACKAGES) {
            Class<?> factoryClass;
            Class<?> filterClass;
            try {
                factoryClass = Class.forName(pkg + ".NashornScriptEngineFactory");
                filterClass = Class.forName(pkg + ".ClassFilter");
            } catch (ClassNotFoundException e) {
                continue;
            }
            try {
                Object filter = Proxy.newProxyInstance(filterClass.getClassLoader(), new Class<?>[]{filterClass},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "exposeToScripts":
                                return DRAFT_ALLOWED_CLASSES.contains((String) args[0]);
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "equals":
                                return proxy == args[0];
                            case "toString":
                                return "DraftScriptClassFilter";
                            default:
                                throw new UnsupportedOperationException(method.getName());
                        }
                    });
                Object factory = factoryClass.getDeclaredConstructor().newInstance();
                Method getEngine = factoryClass.getMethod("getScriptEngine", filterClass);
                return (ScriptEngine) getEngine.invoke(factory, filter);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Could not build the sandboxed script engine from " + pkg, e);
            }
        }
        throw new IllegalStateException("No Nashorn engine with ClassFilter support on the classpath");
    }
}

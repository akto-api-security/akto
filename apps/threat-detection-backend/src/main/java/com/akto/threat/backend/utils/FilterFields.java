package com.akto.threat.backend.utils;

import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FilterFields {

  private FilterFields() {}

  public static final Set<String> AGENTIC_CONTEXTS = Set.of("ENDPOINT", "AGENTIC", "MCP");

  // Order is precedence, mirroring deriveAgenticType in the dashboard's violationsData.js.
  public static final String[][] TYPE_RULES = {
      {"Tool", "tool"}, {"Skill", "skill"}, {"Resource", "resource"}, {"Prompt", "prompt"},
      {"Config", "config|setting"}, {"Tool", "mcp|server"}, {"Prompt", "message|completion|chat"}
  };

  private static final Pattern[] TYPE_PATTERNS = compileTypeRules();
  private static final Pattern BEHAVIOUR = Pattern.compile("(?:^|[\\s{,\"])n?behaviour\"?\\s*[:=]\\s*\"([A-Za-z_]+)\"");

  public static final class Derived {
    public final String agenticType;
    public final String endpointKind;
    public final String behaviour;

    Derived(String agenticType, String endpointKind, String behaviour) {
      this.agenticType = agenticType;
      this.endpointKind = endpointKind;
      this.behaviour = behaviour;
    }
  }

  public static boolean appliesTo(String contextSource) {
    return contextSource != null && AGENTIC_CONTEXTS.contains(contextSource);
  }

  public static Derived derive(String contextSource, String url, String method, String metadata) {
    if (!appliesTo(contextSource)) {
      return null;
    }
    return new Derived(agenticType(url), endpointKind(url, method), behaviour(metadata));
  }

  public static void apply(MaliciousEventDto event) {
    try {
      Derived d = derive(
          event.getContextSource(),
          event.getLatestApiEndpoint(),
          event.getLatestApiMethod() != null ? event.getLatestApiMethod().name() : null,
          event.getMetadata());
      if (d == null) {
        return;
      }
      event.setAgenticType(d.agenticType);
      event.setEndpointKind(d.endpointKind);
      event.setBehaviour(d.behaviour);
    } catch (RuntimeException e) {
      // A derivation problem must never stop the event from being stored.
    }
  }

  // New Violations table Type column: same result as deriveAgenticType(url, method).
  public static String agenticType(String url) {
    String lower = url == null ? "" : url.toLowerCase(Locale.ROOT);
    for (int i = 0; i < TYPE_PATTERNS.length; i++) {
      if (TYPE_PATTERNS[i].matcher(lower).find()) {
        return TYPE_RULES[i][0];
      }
    }
    return "Prompt";
  }

  // Old UI method badge: same result as getMethod in GetPrettifyEndpoint.jsx for the agentic categories.
  public static String endpointKind(String url, String method) {
    String path = url == null ? "" : url;
    int query = path.indexOf('?');
    if (query >= 0) {
      path = path.substring(0, query);
    }
    if (path.contains("/plugin/")) return "PLUGIN";
    if (path.contains("/config/")) return "CONFIG";
    if (path.contains("tool")) return "TOOL";
    if (path.contains("skill")) return "SKILL";
    if (path.contains("resource")) return "RESOURCE";
    if (path.contains("prompt")) return "PROMPT";
    if (path.contains("server")) return "SERVER";
    return method;
  }

  public static String behaviour(String metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return null;
    }
    Matcher m = BEHAVIOUR.matcher(metadata);
    return m.find() ? m.group(1) : null;
  }

  private static Pattern[] compileTypeRules() {
    Pattern[] patterns = new Pattern[TYPE_RULES.length];
    for (int i = 0; i < TYPE_RULES.length; i++) {
      patterns[i] = Pattern.compile(TYPE_RULES[i][1]);
    }
    return patterns;
  }
}

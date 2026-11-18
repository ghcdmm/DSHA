package com.deepseekharness.app.termux;

import java.util.regex.Pattern;

/** Execute only user-installed Termux scripts, never arbitrary Agent shell source. */
public final class TermuxScriptPolicy {
  private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_-]{0,31}");
  public static final String HOME = "/data/data/com.termux/files/home";
  public static final String SCRIPTS = HOME + "/.dsha-bridge/scripts";
  public static final String BASH = "/data/data/com.termux/files/usr/bin/bash";
  private TermuxScriptPolicy() {}

  public static boolean validName(String name) {
    return name != null && NAME.matcher(name).matches();
  }
  public static String scriptPath(String name) {
    if (!validName(name)) throw new IllegalArgumentException("INVALID_SCRIPT_NAME");
    return SCRIPTS + "/" + name + ".sh";
  }
  public static boolean validArgument(String value) {
    if (value == null || value.length() > 200) return false;
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (ch == 0 || ch < 0x20 || ch == 0x7f) return false;
    }
    return true;
  }
}

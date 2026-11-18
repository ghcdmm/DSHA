package com.deepseekharness.app.termux;

import static org.junit.Assert.*;
import org.junit.Test;

public final class TermuxScriptPolicyTest {
  @Test public void namesAreRestricted() {
    assertTrue(TermuxScriptPolicy.validName("probe"));
    assertTrue(TermuxScriptPolicy.validName("python_3"));
    assertFalse(TermuxScriptPolicy.validName("../bad"));
    assertFalse(TermuxScriptPolicy.validName("Bad"));
    assertFalse(TermuxScriptPolicy.validName("a/b"));
    assertFalse(TermuxScriptPolicy.validName(""));
    assertEquals("/data/data/com.termux/files/home/.dsha-bridge/scripts/probe.sh",
      TermuxScriptPolicy.scriptPath("probe"));
  }
  @Test public void argumentsAreBounded() {
    assertTrue(TermuxScriptPolicy.validArgument("one two"));
    assertFalse(TermuxScriptPolicy.validArgument("x\ny"));
    assertFalse(TermuxScriptPolicy.validArgument("\u0000"));
    assertFalse(TermuxScriptPolicy.validArgument("x".repeat(201)));
  }
}

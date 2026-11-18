package com.deepseekharness.app.termux;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives the one-shot mutable PendingIntent filled by Termux. Not exported. */
public final class TermuxResultReceiver extends BroadcastReceiver {
  public static final String ACTION = "com.deepseekharness.app.termux.EXECUTION_RESULT";
  public static final String REQUEST_ID = "dsha_termux_request_id";

  @Override public void onReceive(Context context, Intent intent) {
    if (intent == null || !ACTION.equals(intent.getAction())) return;
    String id = intent.getStringExtra(REQUEST_ID);
    if (id != null) TermuxExecutionBridge.complete(id, intent.getBundleExtra("result"));
  }
}

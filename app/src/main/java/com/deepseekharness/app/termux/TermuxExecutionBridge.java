package com.deepseekharness.app.termux;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.json.JSONObject;

/**
 * On-demand official Termux RUN_COMMAND bridge. Only scripts deliberately installed
 * by the user under ~/.dsha-bridge/scripts can run, with at most four short args.
 * Unknown execution outcomes are never retried automatically.
 */
public final class TermuxExecutionBridge {
  private static final String PACKAGE = "com.termux";
  private static final String PERMISSION = "com.termux.permission.RUN_COMMAND";
  private static final String SERVICE = "com.termux.app.RunCommandService";
  private static final Semaphore SLOT = new Semaphore(1);
  private static final ConcurrentHashMap<String, CompletableFuture<JSONObject>> PENDING =
      new ConcurrentHashMap<>();

  private TermuxExecutionBridge() {}

  private static String receipt(String state, String reason) {
    try {
      return new JSONObject().put("state", state).put("reason", reason).toString();
    } catch (Exception e) {
      return "{\"state\":\"FAILED\",\"reason\":\"JSON_ERROR\"}";
    }
  }

  private static String preflight(Context ctx) {
    try {
      ctx.getPackageManager().getPackageInfo(PACKAGE, 0);
    } catch (PackageManager.NameNotFoundException e) {
      return "TERMUX_NOT_INSTALLED";
    }
    if (ctx.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED)
      return "RUN_COMMAND_PERMISSION_MISSING";
    return null;
  }

  public static String status(Context ctx) {
    String missing = preflight(ctx);
    return missing == null
        ? receipt("READY", "Check Termux allow-external-apps=true before execution")
        : receipt("NOT_READY", missing);
  }

  public static String run(Context ctx, String name, String[] arguments) {
    if (!TermuxScriptPolicy.validName(name)) return receipt("NOT_SENT", "INVALID_SCRIPT_NAME");
    if (arguments == null || arguments.length > 4) return receipt("NOT_SENT", "INVALID_ARGUMENTS");
    for (String value : arguments)
      if (!TermuxScriptPolicy.validArgument(value)) return receipt("NOT_SENT", "INVALID_ARGUMENTS");
    String missing = preflight(ctx);
    if (missing != null) return receipt("NOT_SENT", missing);
    if (!SLOT.tryAcquire()) return receipt("NOT_SENT", "BUSY");
    String id = UUID.randomUUID().toString();
    CompletableFuture<JSONObject> future = new CompletableFuture<>();
    PENDING.put(id, future);
    PendingIntent callback = null;
    boolean submitted = false;
    try {
      Intent result = new Intent(ctx, TermuxResultReceiver.class);
      result.setAction(TermuxResultReceiver.ACTION);
      result.setData(Uri.parse("dsha-termux://result/" + id));
      result.putExtra(TermuxResultReceiver.REQUEST_ID, id);
      int flags = PendingIntent.FLAG_ONE_SHOT;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
      callback = PendingIntent.getBroadcast(ctx, 0, result, flags);
      Intent run = new Intent("com.termux.RUN_COMMAND");
      run.setClassName(PACKAGE, SERVICE);
      run.putExtra("com.termux.RUN_COMMAND_PATH", TermuxScriptPolicy.BASH);
      int length = arguments.length;
      while (length > 0 && arguments[length - 1].isEmpty()) length--;
      String[] argv = new String[length + 1];
      argv[0] = TermuxScriptPolicy.scriptPath(name);
      System.arraycopy(arguments, 0, argv, 1, length);
      run.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", argv);
      run.putExtra("com.termux.RUN_COMMAND_WORKDIR", TermuxScriptPolicy.HOME);
      run.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
      run.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "DSHA script: " + name);
      run.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", callback);
      ctx.startService(run);
      submitted = true;
      return future.get(25, TimeUnit.SECONDS).toString();
    } catch (TimeoutException e) {
      return receipt("UNKNOWN", "RESULT_TIMEOUT_NO_REPLAY");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return receipt(submitted ? "UNKNOWN" : "NOT_SENT", "INTERRUPTED_NO_REPLAY");
    } catch (Exception e) {
      return receipt(submitted ? "UNKNOWN" : "NOT_SENT", "DISPATCH_OR_CALLBACK_ERROR");
    } finally {
      PENDING.remove(id);
      if (callback != null) callback.cancel();
      SLOT.release();
    }
  }

  static void complete(String id, Bundle bundle) {
    CompletableFuture<JSONObject> future = PENDING.get(id);
    if (future == null) return;
    try {
      if (bundle == null) {
        future.complete(
            new JSONObject().put("state", "UNKNOWN").put("reason", "MISSING_RESULT_BUNDLE"));
        return;
      }
      int err = bundle.getInt("err", -1);
      int exit = bundle.getInt("exitCode", -9999);
      JSONObject result =
          new JSONObject()
              .put("state", err == -1 && exit == 0 ? "COMPLETED" : "FAILED")
              .put("exitCode", exit)
              .put("termuxError", err)
              .put("stdout", bundle.getString("stdout", ""))
              .put("stderr", bundle.getString("stderr", ""))
              .put("errorMessage", bundle.getString("errmsg", ""));
      future.complete(result);
    } catch (Exception e) {
      future.complete(new JSONObject());
    }
  }
}

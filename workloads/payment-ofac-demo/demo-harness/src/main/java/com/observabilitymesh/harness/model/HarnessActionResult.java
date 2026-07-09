package com.observabilitymesh.harness.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HarnessActionResult {

    private final String action;
    private final int requested;
    private int succeeded;
    private int failed;
    private int skipped;
    private final List<String> logs = new ArrayList<>();
    private boolean ok = true;

    public HarnessActionResult(String action, int requested) {
        this.action = action;
        this.requested = requested;
    }

    public String action() {
        return action;
    }

    public int requested() {
        return requested;
    }

    public int succeeded() {
        return succeeded;
    }

    public int failed() {
        return failed;
    }

    public int skipped() {
        return skipped;
    }

    public List<String> logs() {
        return List.copyOf(logs);
    }

    public boolean ok() {
        return ok;
    }

    public void log(String message) {
        logs.add(message);
    }

    public void recordSuccess() {
        succeeded++;
    }

    public void recordFailure() {
        failed++;
    }

    public void recordSkip() {
        skipped++;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public void setSucceeded(int succeeded) {
        this.succeeded = succeeded;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("requested", requested);
        payload.put("succeeded", succeeded);
        payload.put("failed", failed);
        payload.put("skipped", skipped);
        payload.put("ok", ok);
        payload.put("logs", List.copyOf(logs));
        return payload;
    }
}

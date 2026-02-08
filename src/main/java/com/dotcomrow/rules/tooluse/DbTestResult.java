package com.dotcomrow.rules.tooluse;

/**
 * Result fact inserted by the DB test rule.
 */
public class DbTestResult {
    private boolean ok;
    private String message;

    public DbTestResult() {}

    public DbTestResult(boolean ok, String message) {
        this.ok = ok;
        this.message = message;
    }

    public static DbTestResult success(String message) {
        return new DbTestResult(true, message);
    }

    public static DbTestResult failure(String message) {
        return new DbTestResult(false, message);
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "DbTestResult{" + "ok=" + ok + ", message='" + message + '\'' + '}';
    }
}


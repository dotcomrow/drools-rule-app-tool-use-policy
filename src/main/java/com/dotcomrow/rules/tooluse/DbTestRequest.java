package com.dotcomrow.rules.tooluse;

/**
 * Fact used to trigger a simple DB connectivity test rule.
 */
public class DbTestRequest {
    private String message;

    public DbTestRequest() {}

    public DbTestRequest(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "DbTestRequest{" + "message='" + message + '\'' + '}';
    }
}


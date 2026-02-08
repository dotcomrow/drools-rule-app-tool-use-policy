package com.dotcomrow.rules.tooluse;

/**
 * Minimal request fact used by the example rules.
 *
 * Keep this small and POJO-friendly so KIE Server JSON marshalling works.
 */
public class ToolUseRequest {
    private String tool;
    private String userRole;
    private int riskScore;
    private double amount;

    public ToolUseRequest() {}

    public ToolUseRequest(String tool, String userRole, int riskScore, double amount) {
        this.tool = tool;
        this.userRole = userRole;
        this.riskScore = riskScore;
        this.amount = amount;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "ToolUseRequest{"
                + "tool='" + tool + '\''
                + ", userRole='" + userRole + '\''
                + ", riskScore=" + riskScore
                + ", amount=" + amount
                + '}';
    }
}


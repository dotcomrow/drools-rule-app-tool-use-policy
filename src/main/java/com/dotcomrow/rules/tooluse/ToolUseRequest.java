package com.dotcomrow.rules.tooluse;

public class ToolUseRequest {
  private String tool;
  private String userRole;
  private int riskScore;
  private double amount;
  private int accountAgeDays;

  public ToolUseRequest() {
  }

  public ToolUseRequest(String tool, String userRole, int riskScore, double amount, int accountAgeDays) {
    this.tool = tool;
    this.userRole = userRole;
    this.riskScore = riskScore;
    this.amount = amount;
    this.accountAgeDays = accountAgeDays;
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

  public int getAccountAgeDays() {
    return accountAgeDays;
  }

  public void setAccountAgeDays(int accountAgeDays) {
    this.accountAgeDays = accountAgeDays;
  }
}

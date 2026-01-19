package com.dotcomrow.rules.tooluse;

public class ToolUseDecision {
  private String decision;
  private String reason;

  public ToolUseDecision() {
  }

  public ToolUseDecision(String decision, String reason) {
    this.decision = decision;
    this.reason = reason;
  }

  public String getDecision() {
    return decision;
  }

  public void setDecision(String decision) {
    this.decision = decision;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }
}

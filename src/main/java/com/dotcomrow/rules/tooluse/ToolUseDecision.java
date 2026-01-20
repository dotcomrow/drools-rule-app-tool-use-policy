package com.dotcomrow.rules.tooluse;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "ToolUseDecision")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ToolUseDecision")
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

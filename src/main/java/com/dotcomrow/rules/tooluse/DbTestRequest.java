package com.dotcomrow.rules.tooluse;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "DbTestRequest")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DbTestRequest")
public class DbTestRequest {
  private String message;

  public DbTestRequest() {
  }

  public DbTestRequest(String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}


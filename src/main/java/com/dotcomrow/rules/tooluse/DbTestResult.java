package com.dotcomrow.rules.tooluse;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "DbTestResult")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DbTestResult")
public class DbTestResult {
  private boolean ok;
  private String dbUser;
  private String dbName;
  private String schema;
  private String requestId;
  private String error;

  public DbTestResult() {
  }

  public DbTestResult(boolean ok, String dbUser, String dbName, String schema, String requestId, String error) {
    this.ok = ok;
    this.dbUser = dbUser;
    this.dbName = dbName;
    this.schema = schema;
    this.requestId = requestId;
    this.error = error;
  }

  public boolean isOk() {
    return ok;
  }

  public void setOk(boolean ok) {
    this.ok = ok;
  }

  public String getDbUser() {
    return dbUser;
  }

  public void setDbUser(String dbUser) {
    this.dbUser = dbUser;
  }

  public String getDbName() {
    return dbName;
  }

  public void setDbName(String dbName) {
    this.dbName = dbName;
  }

  public String getSchema() {
    return schema;
  }

  public void setSchema(String schema) {
    this.schema = schema;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }
}


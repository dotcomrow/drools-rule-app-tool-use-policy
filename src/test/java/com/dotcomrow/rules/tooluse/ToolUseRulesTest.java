package com.dotcomrow.rules.tooluse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.runtime.ClassObjectFilter;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

class ToolUseRulesTest {
  @Test
  void deniesGuestDestructiveTool() {
    KieSession session = newKieSession();

    ToolUseRequest request = new ToolUseRequest("refund", "guest", 10, 50.0, 30);
    session.insert(request);
    session.fireAllRules();

    Collection<?> decisions = session.getObjects(new ClassObjectFilter(ToolUseDecision.class));
    assertEquals(1, decisions.size());

    ToolUseDecision decision = (ToolUseDecision) decisions.iterator().next();
    assertEquals("DENY", decision.getDecision());

    session.dispose();
  }

  private KieSession newKieSession() {
    KieServices services = KieServices.Factory.get();
    KieContainer container = services.newKieClasspathContainer();
    return container.newKieSession();
  }
}

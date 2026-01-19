package com.dotcomrow.rules.tooluse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

public class ToolUseRulesTest {
  @Test
  void deniesDestructiveToolForGuest() {
    ToolUseDecision decision = evaluate(new ToolUseRequest("refund", "guest", 30, 10.0));
    assertEquals("DENY", decision.getDecision());
  }

  @Test
  void reviewsHighAmount() {
    ToolUseDecision decision = evaluate(new ToolUseRequest("lookup", "member", 10, 250.0));
    assertEquals("REVIEW", decision.getDecision());
  }

  @Test
  void allowsLowRisk() {
    ToolUseDecision decision = evaluate(new ToolUseRequest("lookup", "member", 10, 25.0));
    assertEquals("ALLOW", decision.getDecision());
  }

  private ToolUseDecision evaluate(ToolUseRequest request) {
    KieServices kieServices = KieServices.Factory.get();
    KieContainer container = kieServices.getKieClasspathContainer();
    KieSession session = container.newKieSession("tool-use-ksession");
    try {
      session.insert(request);
      session.fireAllRules();
      Collection<ToolUseDecision> decisions =
          (Collection<ToolUseDecision>) (Collection<?>) session.getObjects(
              object -> object instanceof ToolUseDecision);
      List<ToolUseDecision> list = List.copyOf(decisions);
      assertNotNull(list);
      return list.get(0);
    } finally {
      session.dispose();
    }
  }
}

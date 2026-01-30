package com.dotcomrow.rules.tooluse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

public class ToolUseRulesTest {
  @Test
  public void deniesDestructiveToolForGuest() {
    ToolUseDecision decision = evaluate(new ToolUseRequest("refund", "guest", 30, 10.0));
    assertEquals("DENY", decision.getDecision());
  }

  @Test
  public void reviewsHighAmount() {
    ToolUseDecision decision = evaluate(new ToolUseRequest("lookup", "member", 10, 250.0));
    assertEquals("REVIEW", decision.getDecision());
  }

  @Test
  public void allowsLowRisk() {
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
        Collection<?> objs = session.getObjects(object -> object instanceof ToolUseDecision);
        List<ToolUseDecision> list = objs.stream()
          .filter(ToolUseDecision.class::isInstance)
          .map(ToolUseDecision.class::cast)
          .collect(Collectors.toUnmodifiableList());
      assertNotNull(list);
      return list.get(0);
    } finally {
      session.dispose();
    }
  }
}

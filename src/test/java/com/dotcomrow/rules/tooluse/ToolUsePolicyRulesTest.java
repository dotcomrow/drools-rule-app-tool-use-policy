package com.dotcomrow.rules.tooluse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

public class ToolUsePolicyRulesTest {

    @Test
    public void deniesDestructiveToolForGuest() {
        KieSession ksession = newSession();
        try {
            ksession.insert(new ToolUseRequest("delete", "guest", 10, 0));
            ksession.fireAllRules();

            List<ToolUseDecision> decisions =
                    ksession.getObjects(o -> o instanceof ToolUseDecision).stream()
                            .map(o -> (ToolUseDecision) o)
                            .collect(Collectors.toList());
            assertEquals(1, decisions.size());
            assertEquals("DENY", decisions.get(0).getDecision());
        } finally {
            ksession.dispose();
        }
    }

    @Test
    public void allowsLowRiskSmallAmount() {
        KieSession ksession = newSession();
        try {
            ksession.insert(new ToolUseRequest("read", "user", 5, 10));
            ksession.fireAllRules();

            ToolUseDecision decision =
                    (ToolUseDecision)
                            ksession.getObjects(o -> o instanceof ToolUseDecision).stream()
                                    .findFirst()
                                    .orElse(null);
            assertNotNull(decision);
            assertTrue(decision.getDecision().equals("ALLOW") || decision.getDecision().equals("REVIEW"));
        } finally {
            ksession.dispose();
        }
    }

    private static KieSession newSession() {
        KieServices ks = KieServices.Factory.get();
        KieContainer kc = ks.getKieClasspathContainer();
        return kc.newKieSession("tool-use-ksession");
    }
}


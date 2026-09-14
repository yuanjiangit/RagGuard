package com.aizerohub.ragguard.core.testset;

import com.aizerohub.ragguard.core.model.RagTestCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSetLoaderTest {

    @Test
    void loadsYamlWithAllFields() {
        String yaml = """
                testCases:
                  - id: q1
                    question: What is RagGuard?
                    expectedAnswer: A RAG evaluation framework.
                    expectedContexts:
                      - RagGuard evaluates RAG applications.
                      - RagGuard follows the Ragas paper.
                  - id: q2
                    question: Which license does RagGuard use?
                    expectedAnswer: Apache-2.0.
                """;
        List<RagTestCase> cases = TestSetLoader.loadYaml(yaml);
        assertEquals(2, cases.size());
        assertEquals("q1", cases.get(0).id());
        assertEquals("What is RagGuard?", cases.get(0).question());
        assertEquals("A RAG evaluation framework.", cases.get(0).expectedAnswer());
        assertEquals(2, cases.get(0).expectedContexts().size());
        assertTrue(cases.get(1).expectedContexts().isEmpty());
    }

    @Test
    void loadsJsonAsYamlSubset() {
        String json = """
                {"testCases": [{"id": "j1", "question": "JSON works?", "expectedAnswer": "yes"}]}
                """;
        List<RagTestCase> cases = TestSetLoader.loadYaml(json);
        assertEquals(1, cases.size());
        assertEquals("j1", cases.get(0).id());
    }

    @Test
    void missingQuestion_rejected() {
        assertThrows(TestSetFormatException.class,
                () -> TestSetLoader.loadYaml("testCases:\n  - expectedAnswer: x\n"));
    }

    @Test
    void emptyTestCases_rejected() {
        assertThrows(TestSetFormatException.class, () -> TestSetLoader.loadYaml("testCases: []\n"));
    }

    @Test
    void nonMappingRoot_rejected() {
        assertThrows(TestSetFormatException.class, () -> TestSetLoader.loadYaml("- just\n- a\n- list\n"));
    }

    @Test
    void brokenYaml_rejected() {
        assertThrows(TestSetFormatException.class,
                () -> TestSetLoader.loadYaml("testCases: [unclosed"));
    }
}

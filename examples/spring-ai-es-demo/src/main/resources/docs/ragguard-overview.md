# RagGuard

**RagGuard** is a quality-assurance framework for RAG applications on the JVM.
It turns RAG evaluation into unit tests: four metrics — faithfulness, answer
relevance, context recall and context precision — following the Ragas paper
definitions, with an LLM-as-Judge at temperature 0.

## Why RagGuard

When a team swaps the embedding model, changes chunking, or tweaks a prompt,
the quality delta today is measured by vibes. RagGuard makes the delta a
number: a score drops below threshold, the build fails, the regression is
caught in the pull request — not weeks later by user complaints.

## Metrics

- **Faithfulness**: how much of the answer is supported by the retrieved contexts
- **Answer relevance**: how well the answer addresses the question
- **Context recall**: how much of the ground-truth answer the retrieval covered
- **Context precision**: ranking quality of the retrieved contexts

# RagGuard Examples

Two runnable demos, one per ecosystem. Both evaluate a small RAG pipeline against a YAML test set and are **skipped in CI without credentials** (they call real LLMs).

| Demo | Stack | Evaluation route |
|---|---|---|
| [`spring-ai-es-demo/`](spring-ai-es-demo/README.md) | Spring Boot + Spring AI + Elasticsearch vector store | starter auto-configuration + `@SpringBootTest` |
| [`langchain4j-demo/`](langchain4j-demo/README.md) | langchain4j 1.0, **no Spring** | `@RagTest` JUnit 5 extension with supplier annotations |

Both demos use a small built-in knowledge base about RagGuard itself — the project eats its own dog food. See the [docs index](../docs/README.md) for configuration and metric details.

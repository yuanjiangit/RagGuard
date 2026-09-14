# RagGuard

**你的 RAG 变好还是变坏了，不该靠感觉。**

[English](README.md) | [设计笔记（指标算法）](docs/design/01-ragas-metrics-notes.md)

> RagGuard 是一个面向 JVM 生态的 RAG 应用质量保障框架：把 RAG 评估写成单元测试 —— 效果回退，代码就合并不进去，而不是数周后靠用户投诉才发现。

**状态：🚧 M3 —— 双生态 + 增长功能。** 测试集自动生成、langchain4j 适配、GitHub Action 与双框架示例全部就绪；0.1.0 发布工程见 [docs/release.md](docs/release.md)。欢迎 Watch/Star 一起见证。

## 快速上手（三步）

**1. 引入 starter：**

```xml
<dependency>
  <groupId>com.aizerohub.ragguard</groupId>
  <artifactId>ragguard-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

**2. 把你的 RagChain 定义为 Bean，并准备测试集**（`ragguard-test-set.yml`）：

```yaml
testCases:
  - id: q1
    question: RagGuard 是什么？
    expectedAnswer: 一个 JVM 生态的 RAG 评估框架。
```

**3. 写回归测试：**

```java
@SpringBootTest
class MyRagRegressionTest {
    @Autowired RagGuardFacade ragGuard;

    @Test
    void qualityGate() {
        RagAssertions.assertMetricAtLeast(ragGuard.run(), MetricType.FAITHFULNESS, 0.8);
    }
}
```

配置 `ragguard.report-output-dir` 后，每次评估还会生成自包含的 HTML 报告，并自动与上次运行对比。完整可运行的 Spring AI + Elasticsearch 示例见 [examples/spring-ai-es-demo](examples/spring-ai-es-demo)。

**不想手写测试题？** 从你的文档自动生成候选（LLM 生成 + 去重 + 人工确认）：

```java
TestSetGenerator generator = TestSetGenerator.builder(new SpringAiQuestionGenerator(chatModel)).build();
List<GeneratedTestCase> candidates = generator.generate(Map.of("docs.md", documentText));
Files.writeString(Path.of("candidates.yml"), TestSetWriter.toYaml(candidates)); // 人审后使用
```

用现成的 GitHub Action（[action/](action/README.md)）在每次 PR 上跑评估 —— 效果回退直接拦下，并自动评论指标摘要。

## 为什么做

Python 世界有 Ragas / DeepEval / TruLens 一整层评估生态；Java 世界只有"搭 RAG"的框架（Spring AI、langchain4j），没有"验证 RAG"的框架。换 embedding 模型、改切分策略、调 Prompt 之后，效果变化全靠人工"感觉"。

RagGuard 补上这一层：

- **4 个核心指标**，遵循 [Ragas 论文](https://arxiv.org/abs/2309.15217)定义：忠实度（faithfulness）、答案相关性（answer relevance）、上下文召回率（context recall）、上下文精确率（context precision）
- **LLM-as-Judge**：温度 0 + 结构化输出（JSON schema）+ 可选多数投票 + 逐条论断可解释 —— 每个分数都能下钻到逐条判定理由
- **JUnit 5 扩展**：把评估写成 `@RagTest`，分数低于阈值即构建失败
- **Spring Boot Starter**：Spring AI 自动装配，注入自己的 RagChain 即可
- **HTML 报告**：每题得分、总体分、与上次运行对比，适配 CI
- **成本控制**：判定结果按 (输入 hash, 模型, prompt 版本) 本地缓存

## 模块

| 模块 | 说明 |
|---|---|
| `ragguard-core` | 指标引擎、测试集模型、judge 抽象、测试集生成 —— 不依赖任何框架 |
| `ragguard-junit5` | `@RagTest` / `RagAssertions` JUnit 5 扩展 |
| `ragguard-spring-boot-starter` | Spring AI 自动装配（judge、嵌入、测试集生成器） |
| `ragguard-langchain4j` | langchain4j 适配（judge + 嵌入） |
| `ragguard-report` | 自包含 HTML 报告 |

## 路线图

| 里程碑 | 范围 |
|---|---|
| **M0**（第 1~2 周） | 仓库骨架、设计笔记、CI —— *进行中* |
| **M1**（第 3~6 周） | 核心指标引擎 + judge 抽象 + 离线单元测试 |
| **M2**（第 7~10 周） | Spring Boot starter、HTML 报告、端到端示例、0.1.0 发布 |
| **M3**（第 11~14 周） | 测试集自动生成、GitHub Action、langchain4j 适配 |
| **M4**（第 15~16 周） | 报告增强、文档完善、打磨 —— 0.3.0 |

## 本地构建

```bash
mvn verify
```

需要 JDK 17+。CI 在每次 push 和 PR 上执行同样的命令。

## 参与贡献

见 [CONTRIBUTING.md](CONTRIBUTING.md)。设计讨论在 [docs/design/](docs/design/)。

## 许可证

[Apache License 2.0](LICENSE)

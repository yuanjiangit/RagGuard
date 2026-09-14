# RagGuard 中文快速上手

> 完整背景见 [README.zh-CN.md](../../README.zh-CN.md)。本文面向第一次接入 RagGuard 的 Java/Spring 团队。

## 0. 你需要准备什么

- JDK 17+ 的 Spring Boot 3.x 项目（langchain4j 项目见 [langchain4j 示例](../../examples/langchain4j-demo)）
- 一个能调用的 LLM（给 judge 用，便宜模型即可，如 `gpt-4o-mini`）
- 十分钟

## 1. 引入 starter

```xml
<dependency>
  <groupId>com.aizerohub.ragguard</groupId>
  <artifactId>ragguard-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## 2. 定义你的 RagChain（被测系统）

RagGuard 不关心你的 RAG 怎么搭——只需要它实现一个接口：

```java
@Component
public class MyRagChain implements RagChain {
    @Override
    public RagAnswer answer(String question) {
        // 你的检索：ES / Milvus / 任何向量库
        List<String> contexts = myRetriever.topK(question, 3);
        // 你的生成：Spring AI / 任何 LLM
        String answer = myLlm.answer(question, contexts);
        return new RagAnswer(answer, contexts);   // contexts 必须如实返回！
    }
}
```

> **关键**：`contexts` 是检索层真实返回的片段。它决定 context recall / context precision 能否评估你的检索质量。

## 3. 写测试集

`src/main/resources/ragguard-test-set.yml`（JSON 也可以）：

```yaml
testCases:
  - id: q1                          # 可省略，默认 case-N
    question: RagGuard 支持哪些指标？
    expectedAnswer: 忠实度、答案相关性、上下文召回率、上下文精确率四个指标。
  - id: q2
    question: RagGuard 如何控制 judge 成本？
    expectedAnswer: 判定结果按模型、prompt 版本和输入内容哈希做本地缓存，未变化的条目不重复判定。
```

题目从哪来？——**手写几道 + 用生成器从你的文档批量生成候选再人工确认**（见 §7），后者是推荐路径。

## 4. 配置（application.yml）

```yaml
ragguard:
  test-set: classpath:ragguard-test-set.yml
  report-output-dir: ragguard-reports      # 生成 HTML 报告与运行历史
  report-title: 我的 RAG 质量报告
  parallelism: 2                           # 并发评估，按 judge 限流调整
  judge-cache-enabled: true                # 判定缓存（强烈建议开）
  judge-model-id: gpt-4o-mini              # 参与缓存 key
```

全部配置项见 `RagGuardProperties`。

## 5. 写质量门测试

```java
@SpringBootTest
class RagQualityGateTest {

    @Autowired RagGuardFacade ragGuard;

    @Test
    void 质量不回退() {
        EvaluationReport report = ragGuard.run();
        RagAssertions.assertMetricAtLeast(report, MetricType.FAITHFULNESS, 0.8);
        RagAssertions.assertMetricAtLeast(report, MetricType.CONTEXT_RECALL, 0.6);
        // 分数低于阈值 → 测试失败，失败信息里带逐条论断的判定理由
    }
}
```

挂进 CI 后：**换 embedding 模型、改切分、调 prompt 的 PR，效果回退直接变红**。

## 6. 看报告

`ragguard-reports/` 下每次评估产出：

| 文件 | 内容 |
|---|---|
| `ragguard-report-<时间戳>.html` | 自包含报告：总体分、每题得分、逐条论断下钻、指标趋势图 |
| `ragguard-latest-run.json` | 上次运行的摘要（供下次对比） |
| `ragguard-history.jsonl` | 全部历史运行（趋势图数据源） |

排障对照：**忠实度低** → 生成层幻觉；**答案相关性低** → prompt 跑偏；**召回率低** → 漏检，查检索层；**精确率低** → 噪声片段排前了，查融合排序/rerank。

## 7. 从文档生成测试集（最大杠杆）

```java
QuestionGenerator generator = new SpringAiQuestionGenerator(chatModel);
TestSetGenerator tsGen = TestSetGenerator.builder(generator)
        .embedding(embeddingModel)     // 可选：近重复检测
        .maxQuestionsPerChunk(3)
        .build();
List<GeneratedTestCase> candidates =
        tsGen.generate(Map.of("产品手册.md", documentText));
Files.writeString(Path.of("candidates.yml"), TestSetWriter.toYaml(candidates));
```

生成的 `candidates.yml` **必须人工过一遍**：删幻觉题、改问法、补真实用户问法，然后重命名为 `ragguard-test-set.yml` 提交进版本库。生成的题目是候选，不是基准。

## 8. CI 上跑（PR 自动评估）

见 [action/README.md](../../action/README.md)：GitHub Action 在每个 PR 上跑评估、失败即拦截，并把指标摘要评论到 PR。**记得把 `OPENAI_API_KEY` 配置成仓库 Secret。**

## 9. 成本

一次全量评估的 judge 调用 ≈ **每题 4 次结构化调用**（四个指标各一次，空输入自动跳过不调用）。判定缓存开启后，测试集与文档未变化的条目**零调用**。详见 [成本估算](../cost-estimation.md)。

## 常见问题

**Q：judge 打分靠谱吗？** 温度 0 + 结构化输出 + 解析失败重试，同题多次判定方差数据我们公开（跑 `examples/spring-ai-es-demo` 的 `JudgeStabilityTest` 得到你自己的数据）。分数用于回归对比（相对变化），比追求绝对准确更稳健。

**Q：缓存文件要不要提交进 git？** 不要。`ragguard-judge-cache.yml`、`ragguard-reports/` 已在 .gitignore。

**Q：judge 也用我要测的那个模型吗？** 可以，但建议 judge 用另一个便宜模型，避免同源偏差。

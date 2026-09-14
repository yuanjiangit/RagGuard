# 设计笔记 02：Python 评估生态调研与 DeepEval 实现笔记

> 状态：M0 草稿。目的：① 确认 Java 生态的结构性空白仍然成立；② 从 Ragas / DeepEval / TruLens 的实现里吸取经验教训；③ 明确 RagGuard 的差异点，避免"被指抄袭 Ragas"的质疑有实锤空间。

## 1. 生态速览（RagGuard 立项时点）

| 项目 | 定位 | 对 RagGuard 的参考价值 |
|---|---|---|
| **Ragas** | RAG 指标定义的事实标准（论文 + 库），四指标即出自这里 | 算法来源，公开引用 |
| **DeepEval**（Confident AI） | "Pytest for LLM"——把 LLM 评估写成 pytest 测试 + 断言 | **与 RagGuard 定位最像**，重点调研（§3） |
| **TruLens** | 追踪 + 反馈函数，偏可观测性/实验对比平台 | 架构启示：反馈函数抽象、追踪与评估的关系 |
| **promptfoo** | 声明式配置驱动的 LLM 测试工具（yaml 用例 + 断言矩阵） | 测试集 schema 设计、A/B 对比矩阵的 UX 参考 |
| OpenAI Evals / LangSmith 等 | 厂商系评估方案 | 锁定厂商的风险提示——RagGuard 走开放路线 |

**结论**：Python 生态在 2023–2025 已把"指标算法"和"评估运行器"两层都铺满了；Java/JVM 生态只有"搭 RAG"的框架（Spring AI、langchain4j），没有独立成型的"验证 RAG"框架层。空白确认成立。

## 2. Ragas 库实现层面的经验（论文之外的工程事实）

读论文之外， Ragas 库的演进给了几条论文里没有的教训：

1. **指标一直在变**。Ragas 的指标定义与 prompt 在版本间多次调整（context precision 改过判定基准，新增过 noise sensitivity 等）。**教训**：RagGuard 的 prompt 必须带版本号并参与缓存 key，否则缓存结果与指标语义错位。
2. **judge prompt 是护城河也是负担**。社区对 Ragas 的常见抱怨是 prompt 黑盒、改不动。**机会**：RagGuard judge prompt 全部开源在 `docs/` + 代码里可整段替换，把"可校准"做成卖点。
3. **API 形态从函数式迁到对象式**（单例 evaluate → metrics 对象组合）。**教训**：RagGuard 一开始就用"Metric 为一等对象 + 显式配置"，不留全局静态门面。
4. **LLM/Embedding 依赖抽象**：Ragas 抽象了 `BaseRagasLLM`。**印证**：RagGuard 的 `Judge` / `EmbeddingModel` 接口设计方向正确，且我们更进一步——core 连 JSON 解析库都慎加，保持零框架依赖。

## 3. DeepEval 实现笔记（重点）

DeepEval 是"把 LLM 评估做成 pytest"的直接同类，值得逐层拆：

### 3.1 核心抽象

- `LLMTestCase`（input / actual_output / expected_output / retrieval_context）≈ RagGuard 测试集模型。**借鉴**：字段命名与 Ragas 对齐（question/answer/contexts），降低用户跨生态的心智成本。
- `BaseMetric`：`measure(test_case) -> score` + `reason`。**借鉴**：我们的 `Metric` 接口返回 `MetricResult`（分数 + 逐条论断明细 + judge 原始输出），比 DeepEval 的 reason-only 可解释性更强——这是我们宣称的差异化。
- `assert_test(test_case, metrics, threshold)`：pytest 集成层。**对应物**：`@RagTest` + `RagAssertions`，语义一致但用 JUnit 5 扩展机制（`InvocationInterceptor` / 参数注入）而非 pytest fixture。

### 3.2 值得抄的工程细节

1. **G-Eval 作为通用打分骨架**：chain-of-thought 生成评分步骤 → 逐步打分 → 加权聚合。RagGuard 的 P0 四指标都是 Ragas 算法，但 `Judge` 抽象预留 G-Eval 式"自定义评分准则"扩展位（P1+，自定义指标是 DeepEval 的付费增长点，开源版也可以做）。
2. **JSON 提取容错**：LLM 输出常混入 markdown 代码块/前导文本；DeepEval 内置多级解析。**借鉴**：我们的"1 次原样重试 + 1 次强约束重试"之外，解析层直接支持剥离 ```json 围栏。
3. **合成数据生成**（`Synthesizer`）：从文档生成测试集——与 RagGuard M3 的"测试集自动生成"完全同构，证明该功能是落地的最大杠杆。**行动**：M3 前精读其去重与人工确认流程设计。
4. **指标并发生成**：批量 evaluate 时并发调用。**借鉴**：RagGuard 批量评估用有界并发（默认并发 4，可配），既提速又不触发限流。

### 3.3 DeepEval 的坑（我们避免）

1. **测试与运行时耦合**：pytest 集成里阈值断言抛异常导致"评估失败"与"测试失败"语义混杂。**对策**：RagGuard 区分 `judge_failed`（基础设施问题，测试标记为 error 并给出明确指引）与 `score_below_threshold`（真实质量回退，assertion failure）——CI 语义必须干净。
2. **报表与评估耦合在 CLI**。**对策**：report 模块只依赖 core 的结果模型，入口（CLI / JUnit / CI）都只是生产结果的一方。
3. **0.x API 大改导致社区教程集体失效**。**对策**：M2 的 0.1.0 起，core 公共 API 变更必须在 CHANGELOG 标注 BREAKING。

## 4. RagGuard 的差异点声明（写进 README 的话术基础）

1. **生态**：Java/Spring 原生——Spring AI 自动装配、JUnit 5 习惯、Maven 发布、GitHub Action 可直接挂 PR 流程；用户不离开自己的技术栈。
2. **工程化增强而非算法独创**：指标明确引用 Ragas 论文；我们的增量在 judge 稳定性工程（温度 0 + 结构化输出 + 缓存 + 多数投票）、逐条论断可解释下钻、故障模式 → 指标的排障映射。
3. **可解释性**：每个分数能下钻到逐条论断的判定理由与归因 context——这是与"跑个脚本让 GPT 打个分"的差距。
4. **中文场景**：中文切分、中英双语 judge prompt、中文示例测试集——中文社区是我们的主场。

## 5. 对 M1 设计的直接输入（清单）

- [ ] `Judge` / `EmbeddingModel` 接口先行，core 零框架依赖
- [ ] `MetricResult` 携带逐条论断明细 + judge 原始输出（不只是 score/reason）
- [ ] prompt 常量带 `PROMPT_VERSION`，参与缓存 key
- [ ] JSON 解析容错：markdown 围栏剥离 + 重试链
- [ ] `judge_failed` 与 `score_below_threshold` 语义分离
- [ ] 批量评估有界并发（默认 4）
- [ ] 空/缺输入短路返回，不调 judge

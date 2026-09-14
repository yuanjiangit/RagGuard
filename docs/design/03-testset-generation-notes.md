# 设计笔记 03：测试集自动生成（M3，最大杠杆）

> 状态：M3 实现笔记。功能：从用户文档自动抽取 QA 对，产出**待人工确认**的测试集候选清单。
> 定位：这是 Ragas "TestsetGenerator" / DeepEval "Synthesizer" 同构的能力，也是落地路径上离用户痛点最近的一步——"我知道要写测试集，但 50 道题谁来写？"

## 1. 产品决策：生成物是"候选清单"，不是"可用测试集"

自动生成的 QA 对存在两类系统性风险：**幻觉**（答案不在文档里，LLM 看着合理编的）与**偏置**（生成的题偏向文档表面措辞，覆盖不了真实用户问法）。因此 RagGuard 的生成管线刻意停在"候选"这一步：

```
documents ──► chunking ──► LLM 生成 QA ──► 去重 ──► candidates（YAML）
                                                        │
                                              人工：删错的、改问法、补真实问法
                                                        ▼
                                              ragguard-test-set.yml（评估基准）
```

**为什么不做"一键全自动"**：测试集是评估的 ground truth。用它验收 RAG 效果的前提是它本身可信——由 LLM 自己生成又自己验收，等于既当运动员又当裁判。人工确认是成本与可信度之间的正确交易点：生成把"写 50 道题"降到"审 50 道题"，这是数量级的杠杆；最后 10% 的人审保住数据的裁判权。

工程佐证：`TestSetWriter.toYaml()` 的输出直接走 `TestSetLoader` 的加载路径 —— 候选文件与最终测试集是同一格式，确认动作只是"编辑文件"，没有第二套概念。

## 2. 管线各环节的算法与决策

### 2.1 Chunking（复用评估的语义）

按段落边界合并成 ~1600 字符的块（`maxChunkChars` 可配），与 RAG 应用自身的 ingestion 粒度天然对齐——**用接近你实际切分策略的粒度生成题目，题目的难度分布才接近真实检索场景**。超长段落硬切，空文档跳过。

### 2.2 生成（结构化输出，与 judge 同一套纪律）

`QuestionGenerator` 接口（core），Spring AI / langchain4j 各一个薄适配。内置 prompt 约束：答案必须能从 SOURCE 推出（grounding 约束写在 prompt 里，人审兜底）；问题自包含（禁止"在这份文档里"式提问）；与源文档同语言（中文文档生成中文题，避免跨语言评估失真——同 [01 号笔记 §2] 的语言一致性原则）。温度 0 + JSON schema + 失败重试一次，与 judge 完全同构。

### 2.3 去重（两级，成本从低到高）

1. **规范化文本精确去重**：小写 + 去标点/多余空白后比对 —— 零成本，拦住 LLM 高频复述。
2. **嵌入近重复检测**（可选，提供 `EmbeddingModel` 即启用）：问题嵌入余弦 ≥ 0.92 视为近重复。阈值取宽（宁漏勿错杀）——重复题只浪费人审时间，误杀丢题目。嵌入调用失败不阻断生成（去重是 best-effort，永远不能挡住主流程）。

### 2.4 容错：单块失败不炸全局

某个 chunk 触发限流/超时 → 跳过该 chunk 继续跑（候选少几道好过整批失败）。人审阶段天然允许缺失。

## 3. 与生态的差异声明

- Ragas `TestsetGenerator` 重在自动合成（含 evolution/多跳改造），面向"造基准"；RagGuard 面向**回归测试**：产物必须过人审、必须可长期维护（YAML 进版本库、diff 可 review）。这是"benchmark 思维"与"test-suite 思维"的分叉。
- DeepEval `Synthesizer` 走到"context 的多级合成"，功能更强也更深不可控；M3 故意做窄（单跳 QA），把复杂合成留给 P2 按需加。

## 4. 使用方式（对应实现）

```java
QuestionGenerator g = new SpringAiQuestionGenerator(chatModel); // 或 LangChain4j 内置等价物
TestSetGenerator generator = TestSetGenerator.builder(g)
        .embedding(embeddingModel)          // 可选：近重复检测
        .maxQuestionsPerChunk(3)
        .build();
List<GeneratedTestCase> candidates = generator.generate(documentsByName);
Files.writeString(Path.of("generated-candidates.yml"), TestSetWriter.toYaml(candidates));
// 人工编辑 generated-candidates.yml → 重命名为 ragguard-test-set.yml → 提交进版本库
```

starter 中 `SpringAiQuestionGenerator` 为自动装配 Bean（`@ConditionalOnMissingBean(QuestionGenerator.class)`）。

## 5. 遗留与 P2 方向

- [ ] 多跳/改造式题目（Ragas evolution 风格）——等有真实用户数据再决定是否做
- [ ] 去重阈值校准实验（0.92 是拍的，跑几批真实文档后回填依据）
- [ ] 候选清单的"已确认"标记字段（现靠人工编辑文件，够用；量大后再考虑交互式 CLI）
- [ ] 成本估算文档：N 文档 → chunks × 一次生成调用的 token 公式

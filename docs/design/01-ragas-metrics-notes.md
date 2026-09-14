# 设计笔记 01：四个核心指标的算法设计（源自 Ragas 论文）

> 状态：M0 草稿，随 M1 实现持续修订。
> 参考：Es et al., *RAGAS: Automated Evaluation of Retrieval Augmented Generation*, arXiv:2309.15217（EACL 2023 Demo）。
> 本文是 RagGuard 的公开算法笔记 —— 我们明确引用论文，定位是"Java 生态实现 + 工程化增强"，算法独创性不是差异点。

## 0. 统一符号

一次评估的最小单元（与 Ragas 论文记号一致）：

| 符号 | 含义 | RagGuard 测试集字段 |
|---|---|---|
| `q` | 用户问题 | `question` |
| `a` | 被测 RagChain 生成的答案 | （运行时产生） |
| `c₁…cₖ` | 检索到的上下文片段，按检索排名排列 | （运行时产生） |
| `a*` | 标准答案 | `expected_answer` |
| `c*₁…c*ₘ` | 标注的理想上下文 | `expected_contexts` |

所有 judge 调用统一约束：**温度 0 + JSON schema 结构化输出 + prompt 带版本号**（版本号参与缓存 key，见 §6）。

---

## 1. 忠实度 Faithfulness

**衡量**：答案 `a` 有多少内容能被检索到的上下文 `c₁…cₖ` 支撑 —— 检测"模型幻觉/编造"。

**三步算法**：

1. **拆解（claim decomposition）**：judge 把 `a` 拆成原子论断集合 `S(a) = {s₁…sₙ}`。原子论断 = 不可再拆的事实陈述，一句含两个事实的话应拆成两条。
2. **逐条判定（verification）**：对每条 `sᵢ`，judge 判定"仅凭 `c₁…cₖ` 能否推出该论断"，输出三值结果：`supported / refuted / not_enough_info`。
3. **聚合**：

```
faithfulness = |supported 论断数| / |S(a)|
```

**工程决策（与论文的差异点）**：

- 论文将无法验证的论断记为 1（宽容）或直接丢弃；RagGuard 采用**三值判定并把 `refuted` 与 `not_enough_info` 都计为分母、不计入分子**，即对幻觉更严格。`refuted` 与 `not_enough_info` 在报告中区分展示——前者是"答案与上下文矛盾"（更严重），后者是"上下文没接住"。
- 拆解与判定**合并为一次结构化调用**（输出 `{claims: [{text, verdict, reason}]}`），而不是论文的两轮独立调用——省一半 token 与延迟。M1 先按合并版实现，若实测拆解质量下降，退回两轮制并保留开关。
- 空答案 / 空上下文：直接返回 0 或 1 并打标记，不调用 judge（成本与确定性考虑）。

**可解释性输出**：每条论断的原文、判定结果、judge 给出的理由 —— 全部落入评估结果对象，供 HTML 报告下钻展示。

---

## 2. 答案相关性 Answer Relevance

**衡量**：答案是否针对问题 —— 检测"答非所问 / 冗长跑题"。

**反向生成法（论文思路，避免依赖标准答案）**：

1. judge 根据 `a` 反向生成 `N` 条"如果答案是这个，用户最可能问出的问题" `q̂₁…q̂_N`（论文取 N=3）。
2. 对每条 `q̂ᵢ` 与原 `q` 计算嵌入余弦相似度：

```
answer_relevance = (1/N) · Σᵢ cos(E(q̂ᵢ), E(q))
```

**工程决策**：

- 依赖可插拔的 `EmbeddingModel` 接口（Spring AI 适配先行，langchain4j 二期），core 中只定义接口与余弦相似度实现，可离线用固定向量 mock。
- 反向生成 prompt 要求问题风格与原问题一致（含语言一致性：中文答案生成中文问题，避免跨语言嵌入稀释相似度）——这是中文场景的关键细节，论文未涉及。
- N 默认 3，可配置；`N` 次生成合并为一次 judge 调用（JSON 数组输出）。
- 变异指标（论文同时提出的 answer correctness，结合 `a*` 的加权 F1）：**不进 P0**，进 backlog —— 需要标准答案且与 context recall 信号重叠。

---

## 3. 上下文召回率 Context Recall

**衡量**：检索层有没有"接住"回答标准答案所需的全部事实 —— 检测"漏检"。这一项完全不需要生成答案参与，是定位检索问题的首选指标。

**算法**：

1. judge 把标准答案 `a*` 拆成原子论断 `{s*₁…s*ₙ}`。
2. 对每条 `s*ᵢ`，judge 判定"能否将其**归因**到 `c₁…cₖ` 中的某条上下文"，并对每条论断要求给出**归因到的 context 编号**。
3. 聚合：

```
context_recall = |可归因论断数| / |总论断数|
```

**工程决策**：

- 与忠实度复用**同一套原子论断拆解的数据结构**（`Claim`），复用缓存与 prompt 骨架。
- 要求 judge 输出归因到的 context 下标 —— 报告里能直接指出"第 3 条论断需要的信息，检索结果里没有"，这是"漏检"最直观的证据链。
- `expected_contexts` 字段在 P0 的 context recall 中**不直接参与计算**（算法只用 `a*` + 实际检索结果）；它保留在测试集 schema 中的原因：① 供后续 answer correctness 类指标使用；② 供人工核对检索质量；③ 测试集自动生成（M3）会产出它。在文档中明确这一点，避免用户误填。

---

## 4. 上下文精确率 Context Precision

**衡量**：检索返回的片段排序质量 —— 有用的片段是否排在前面。这一项**直接衡量 BM25 + 向量 + rerank 融合排序的效果**，是混合检索团队最该盯的指标。

**算法**（论文定义）：

1. judge 对每条 `cᵢ` 判定"它对得出 `a*`（或对回答 `q`）是否有用"，得二值 `vᵢ ∈ {0,1}`。
2. 按检索排名计算**平均精确率（AP）**：

```
context_precision = (1/|useful|) · Σᵢ ( precision@i · vᵢ )
   其中 precision@i = ( Σ_{j≤i} vⱼ ) / i
```

即：有用的片段排得越靠前，精确率加权越高。

**工程决策**：

- 判定基准用"对回答 `q` 是否有用"（论文的 no-ground-truth 变体），而非"对 `a*` 是否必要"——前者不依赖标准答案，测试集更易写；作为配置项保留后者。
- k 条上下文合并为**一次 judge 调用**输出 `[{index, useful, reason}]`，并对输出做完整性校验（缺下标则重试一次，仍缺则该条按"无用"计并打 `judge_incomplete` 标记）。
- 报告展示 AP 曲线（precision@i 随 i 变化），rerank 调参时一眼看出"排序前移/后移"。

---

## 5. 指标与故障模式对照（给用户的排障指南）

| 症状 | 高概率原因 | 先看哪个指标 |
|---|---|---|
| 答案编造了上下文里没有的内容 | 生成层幻觉 | faithfulness ↓ |
| 答案啰嗦、跑题、答非所问 | prompt / 生成层 | answer relevance ↓ |
| 该答上的问题答不上，答案缺关键事实 | 漏检 | context recall ↓ |
| 答案对了但慢、贵、易被噪声带偏 | 排序差，噪声片段在前 | context precision ↓ |

这个对照表会进文档站和 README —— "分数低 → 该改哪一层"是用户最想要的答案。

---

## 6. 工程要点：稳定性、成本、可解释性

### 6.1 judge 稳定性

- 温度 0 + 结构化输出（JSON schema 约束，Spring AI `BeanOutputConverter` 或手写解析兜底）。
- 解析失败重试：1 次原样重试 + 1 次附加"只输出 JSON"提示重试，仍失败则该指标标记 `judge_failed`，不静默给 0 分。
- **多数投票（可选）**：同一判定跑 3 次取众数，供付费 API 用户兜底 —— 默认关闭，配置开启。
- 公开方差数据：README 报告同题集多次运行的标准差（"LLM 打分靠谱吗"用数据回应，见运营计划）。

### 6.2 成本控制

- 判定结果缓存 key = `hash(输入内容) + model + prompt版本`，本地文件存储；测试集未变的项增量复用。
- 一次全量评估的 judge 调用次数 = O(|测试集| × (claims拆解 + 判定合并) )，文档给出按题估算的 token 公式与实测均值。

### 6.3 离线可测性（M1 的硬约束）

- judge 与 embedding 都是接口（`Judge` / `EmbeddingModel`），单元测试注入**固定 mock 输出**，四指标的计算逻辑（拆解结果 → 聚合公式）全部离线可验证，不碰网络。
- 顺带的架构红利：换模型、换 prompt 只影响接口实现，聚合公式层零改动。

---

## 7. M1 实现拆解（下一步）

1. core：`Claim`、`MetricType`、`MetricResult`（含逐条判定明细）、`EvaluationResult`、测试集模型（YAML/JSON 加载）。
2. core：`Judge` / `EmbeddingModel` 接口 + 余弦相似度 + 四个 `Metric` 实现（聚合公式 + 调用编排）。
3. 单元测试：固定 mock 数据覆盖每个指标的边界（空答案、空上下文、judge 输出不完整、全 supported / 全 refuted）。
4. junit5 模块：`@RagTest` + `RagAssertions`（阈值断言）。

# RagGuard — RAG 应用的质量保障框架 · 开工计划

> 一句话定位：**给你的 RAG 应用做"单元测试"和回归测试 —— 效果回退，代码就合并不进去。**
>
> Slogan（候选）：Stop guessing. Regression-test your RAG. / 你的 RAG 变好还是变坏了，不该靠感觉。

---

## 1. 项目命名与定位

| 候选名 | 说明 |
|---|---|
| **RagGuard**（推荐） | "guard"直接传达"守护效果、防回退"，好念好记，域名/组织名大概率可用 |
| rag-eval4j | 描述性强，但平淡，传播性弱 |
| RagBench | 有"基准"含义，但容易和 benchmark 数据集项目混淆 |

> 注意：定名前先去 GitHub / Maven Central / 域名各搜一遍，确认没被占用（本计划撰写时无法联网核实，需人工确认）。

- **语言与生态**：Java 17+，Spring Boot 3.x 优先适配 Spring AI，二期适配 langchain4j
- **License**：Apache-2.0（企业友好，利于传播）
- **目标用户**：
  1. 用 Java/Spring 技术栈把 RAG 推向生产的团队（金融、政企、传统企业 SaaS —— 中文社区的主力人群）
  2. 写 Spring AI / langchain4j 教程、做企业内训的开发者
- **不做什么**（重要边界）：不做 RAG 编排框架、不做知识库问答产品、不做 LLM 网关 —— 只做"验证 RAG 效果"这一层，与 Spring AI / langchain4j 是互补而非竞争关系。

## 2. 为什么是这个项目（立项依据摘要）

- Python 世界有 Ragas / DeepEval / TruLens 一整层评估生态；Java 世界只有"搭 RAG"的框架，没有"验证 RAG"的框架，是结构性空白。
- RAG 落地团队的真实痛点：换 embedding 模型、改切分策略、调 Prompt 之后，效果变化全靠人工"感觉"，坏改动往往数周后才被用户投诉发现。
- 选题一句话可讲清、演示效果直观（一张效果回退报告截图胜过千言），适合内容传播。

## 3. 功能矩阵与优先级

### P0 — MVP（没有它就不发布）

| 功能 | 说明 |
|---|---|
| 核心指标引擎 | 忠实度 faithfulness、答案相关性 answer relevance、上下文召回率 context recall、上下文精确率 context precision，共 4 个 |
| LLM-as-Judge | 基于 Spring AI 调用大模型打分；温度 0；判定 prompt 内置且可自定义 |
| 嵌入相似度计算 | 可插拔 EmbeddingModel 接口（Spring AI 适配先行） |
| 测试集定义 | YAML/JSON 格式的测试集：question + expected_answer + expected_contexts |
| Spring Boot Starter | `ragguard-spring-boot-starter`，自动装配，用户注入自己的 RagChain 即可 |
| JUnit 5 扩展 | `@RagTest` / `RagAssertions`，把评估写成测试，效果低于阈值即失败 |
| 基础 HTML 报告 | 每题得分、总体分、与上次运行对比（本地文件对比即可，先不做服务端存储） |
| 1 个端到端示例 | example 项目：Spring AI + ES 混合检索的小知识库 + 一套测试集 + CI 配置 |

### P1 — 发布后 2~3 个月内的增长引擎

| 功能 | 说明 |
|---|---|
| 测试集自动生成 | 从用户文档自动抽 QA 对（LLM 生成 + 去重 + 人工确认清单），这是落地的最大杠杆 |
| GitHub Action | `ragguard-action`，PR 上跑评估并评论报告摘要 |
| langchain4j 适配模块 | 双生态覆盖，两个官方社区都是流量入口 |
| 检索器适配器 | ES 混合检索（BM25+向量+RRF/融合排序）的内置示例与指标挂钩 |
| 报告增强 | 指标趋势图、逐题 judge 评分理由展示 |
| 中文场景优化 | 中文切分、judge prompt 中英双语版本、中文示例测试集 |

### P2 — 产品化方向（视数据决定）

- 切分质量评估（ingestion 层）：不同切分策略的效果对比 —— 叙事升级为"从 ingestion 到检索到生成的全链路质量平台"
- 历史趋势服务（本地 SQLite 或 Docker 一键起）→ 可视化 Dashboard → SaaS 化探索
- Prompt/配置 A/B 回放：一次评估跑多套配置，自动出对比矩阵
- Maven/Gradle 插件、IDE 插件

## 4. 核心指标算法设计概要

四个指标均参考 Ragas 论文定义（README 中引用论文，增强可信度），judge prompt 自研并开源，方便社区校准：

1. **忠实度 faithfulness**：LLM 把 answer 拆解成原子论断（claims）→ 对每个论断判定"能否由检索到的 contexts 推出" → 忠实度 = 被支持的论断数 / 总论断数。
2. **答案相关性 answer relevance**：LLM 从 answer 反向生成 N 个"这个问题可能的问法" → 计算它们与原 question 的嵌入相似度均值。（反向生成法可以避免"标准答案"依赖）
3. **上下文召回率 context recall**：把 expected_answer 拆成论断，逐条判定"能否归因到检索到的 contexts" → 召回率 = 可归因论断 / 总论断。
4. **上下文精确率 context precision**：对检索返回的 contexts，判定每条是否对回答有用，按排名计算加权精确率（有用片段越靠前分越高）→ 这一项直接衡量 BM25+向量+rerank 融合排序的质量。

工程要点：

- **judge 稳定性**：温度 0 + 结构化输出（JSON schema）+ 可选多数投票（3 次判定取众数，供付费 API 用户兜底）
- **成本控制**：判定结果按 (输入内容 hash, 模型, prompt 版本) 缓存到本地；测试集变更时增量重算
- **可解释性**：每个分数都能下钻到逐条论断的判定理由 —— 这是与"跑个脚本让 GPT 打个分"拉开差距的关键，也是报告传播点

## 5. 仓库与模块结构

```
ragguard/
├── ragguard-core/                  # 指标计算、测试集模型、judge 抽象（不依赖 Spring）
├── ragguard-spring-boot-starter/   # Spring AI 自动装配
├── ragguard-junit5/                # @RagTest 扩展
├── ragguard-langchain4j/           # P1：langchain4j 适配
├── ragguard-report/                # HTML 报告渲染
├── examples/
│   ├── spring-ai-es-demo/          # Spring AI + Elasticsearch 混合检索示例
│   └── langchain4j-demo/           # P1
├── docs/                           # 中英双语文档（GitHub Pages）
├── README.md（英文为主，首屏链接中文版 README.zh-CN.md）
└── LICENSE / CONTRIBUTING.md / CHANGELOG.md
```

- 版本策略：0.x 阶段快速迭代，API 稳定后升 1.0
- 发布到 Maven Central（sonatype），发布前先跑通 examples 的 CI
- CI 里 dogfood：ragguard 自己的文档站如果做检索，就用 ragguard 测自己（"吃自己的狗粮"本身就是宣传素材）

## 6. 里程碑（业余时间，约 4 个月）

### M0 · 第 1~2 周：立项与骨架
- 确定命名，确认 GitHub 组织/Maven groupId 可用
- 建仓库：README（中英）、LICENSE、模块骨架、CI（编译+测试）
- 通读 Ragas 论文与 DeepEval 实现，写下指标计算笔记（docs/design/ 下，公开，吸 star 的细节之一）
- **产出：仓库可访问，设计笔记两篇**

### M1 · 第 3~6 周：核心引擎（最难的一段）
- 实现 4 个指标 + judge 抽象 + 嵌入接口
- 用固定 mock 数据写单元测试，保证指标计算可离线验证
- JUnit 5 扩展 + 最简 YAML 测试集
- **产出：core 在本地能对任意 RagChain 跑出分数**

### M2 · 第 7~10 周：MVP 发布（第一个关键节点）
- Spring Boot starter、基础 HTML 报告
- examples/spring-ai-es-demo 跑通端到端
- 补充真实模型下的稳定性测试（跑 20 次看分数方差）
- 发布 0.1.0 到 Maven Central
- **产出：可安装、可运行、有截图有报告的开源项目**

### M3 · 第 11~14 周：增长功能
- 测试集自动生成（最大杠杆，值得单独写一篇文章）
- GitHub Action + PR 评论集成
- langchain4j 适配模块 + 示例
- **产出：0.2.0，双生态支持**

### M4 · 第 15~16 周：打磨与复盘
- 报告增强、中文文档完善、性能与成本优化
- 收集前几位真实用户的反馈，规划 P2
- **产出：0.3.0 + 复盘文章**

## 7. 冷启动与运营（决定 star 上限的一半因素）

- **内容先行**：每个里程碑配 1~2 篇文章（掘金 / 公众号 / 知乎），选题如《你的 RAG 效果是靠感觉保证的吗》《我用 200 行代码给 RAG 做了回归测试》《换 embedding 模型前，请先跑一遍评估》—— 中文内容是你相对 Python 生态最大的主场优势
- **借官方社区**：向 Spring AI / Spring AI Alibaba、langchain4j 提 issue 或 PR（如提供评估集成示例），争得上游文档/示例的曝光
- **README 首屏**：30 秒动图（评估回退被 CI 拦下）+ 一行 slogan + 快速上手三步；截图质量 = 转发率
- **双语文档**：代码、注释、API 英文；教程与推广中文；Hacker News / Reddit r/java 择机发布英文版
- **数据诚实**：在 README 公开 judge 的稳定性数据（同题多次判定的方差），主动回应"LLM 打分靠谱吗"的质疑，反而建立专业形象

## 8. 风险与对策

| 风险 | 对策 |
|---|---|
| Spring AI / langchain4j API 迭代快，适配成本高 | core 模块不依赖任何框架，框架适配全部隔离在 starter/adapter 层 |
| "LLM 当裁判不稳定"被质疑 | 温度 0 + 结构化输出 + 缓存 + 公开方差数据；提供人工标注对比工具 |
| 指标算法被指抄袭 Ragas | 明确引用论文、注明是"Java 生态实现 + 工程化增强"，差异点是工程与生态而非算法独创 |
| 业余时间断续，烂尾 | 里程碑切小、每 2 周必须有一个"能给人看"的产出；MVP 范围（P0）严格锁死，新想法全进 backlog |
| judge 调用成本吓退用户 | 本地缓存 + 支持便宜模型当 judge + 文档给出每次评估的成本估算 |
| 同类项目出现 | 加速双生态覆盖与中文场景纵深；工程质量与内容运营是护城河 |

## 9. 本周就能做的行动清单

1. 去 GitHub / Maven Central 搜索确认 "ragguard" 等候选名未被占用，定名
2. 建私有仓库，把本文档内容整理进 docs/
3. 通读 Ragas 论文，产出第一篇设计笔记
4. 搭 M0 骨架（半天）：多模块 Maven 工程 + CI
5. 写 README 初稿（先中文，英文版发布前完成）

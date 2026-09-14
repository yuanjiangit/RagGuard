# RagGuard

**你的 RAG 变好还是变坏了，不该靠感觉。**

[English](README.md) | [设计笔记（指标算法）](docs/design/01-ragas-metrics-notes.md)

> RagGuard 是一个面向 JVM 生态的 RAG 应用质量保障框架：把 RAG 评估写成单元测试 —— 效果回退，代码就合并不进去，而不是数周后靠用户投诉才发现。

**状态：🚧 项目启动（M0）。** 指标引擎在 M1 落地，第一个可运行版本（0.1.0）目标在 M2 发布。欢迎 Watch/Star 一起见证。

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
| `ragguard-core` | 指标引擎、测试集模型、judge 抽象 —— 不依赖任何框架 |
| `ragguard-junit5` | `@RagTest` / `RagAssertions` JUnit 5 扩展 |
| `ragguard-spring-boot-starter` | Spring AI 自动装配 |
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

# 发布 0.1.0 到 Maven Central · 操作清单

前提（一次性）：

1. 在 [Sonatype Central Portal](https://central.sonatype.com) 注册账号并创建 namespace（groupId `com.aizerohub`，需验证域名或 GitHub org 的所有权 —— aizerohub 对应 GitHub org / 域名验证）。
2. 本地安装并配置 GPG：`gpg --full-generate-key`，把公钥发布到 keyserver（central-publishing 插件会校验签名）。
3. 在 `~/.m2/settings.xml` 配置凭据：

```xml
<settings>
  <servers>
    <server>
      <id>central</id>  <!-- 与 release profile 的 publishingServerId 对应 -->
      <username>${CENTRAL_PORTAL_TOKEN_USER}</username>
      <password>${CENTRAL_PORTAL_TOKEN_PASS}</password>
    </server>
  </servers>
</settings>
```

发布步骤：

```bash
# 1. 去 SNAPSHOT（root pom 及各模块 parent 引用）
mvn versions:set -DnewVersion=0.1.0 -DprocessAllModules

# 2. 全量验证（CI 同款命令）
mvn clean verify

# 3. 签名并发布到 Central Portal
mvn -P release -DskipTests=false clean deploy

# 4. Portal 上确认 autoPublish 生效；发布成功后打 git tag
git tag v0.1.0 && git push origin v0.1.0

# 5. GitHub Releases 页面写 0.1.0 release notes（摘自 CHANGELOG）
```

发布前自查：

- [ ] `docs/design/` 笔记口径与实现一致
- [ ] README 快速上手三步可照抄运行
- [ ] LICENSE / NOTICE 无误；所有 artifact 带 sources + javadoc + GPG 签名（release profile 自动）
- [ ] examples/spring-ai-es-demo 在真实模型下跑通（评估测试 + 稳定性测试至少各跑一次）
- [ ] 稳定性数据（同题 20 次的 mean/stddev）已记录到 README 或 docs —— "LLM 打分靠谱吗"用数据回应

发布后 0.x 阶段规则：API 变更必须在 CHANGELOG 标注 BREAKING；下个版本号 0.2.0（M3：测试集自动生成 + GitHub Action + langchain4j 适配）。

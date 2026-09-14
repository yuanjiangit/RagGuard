# ragguard-action

Run RagGuard evaluation on every pull request and comment the metric summary.

## Usage

In the repository of a project that uses RagGuard (Spring Boot starter +
`ragguard.report-output-dir` configured):

```yaml
# .github/workflows/ragguard.yml
name: RagGuard
on:
  pull_request:
  push:
    branches: [ main ]

jobs:
  evaluate:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pull-requests: write
    steps:
      - uses: actions/checkout@v4
      - uses: yuanjiangit/RagGuard/action@main
        with:
          maven-command: 'mvn -B --no-transfer-progress verify'
          report-dir: 'ragguard-reports'
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
```

The action:

1. sets up the JDK and runs your Maven command (your RagGuard evaluation tests run as part of the build),
2. reads `ragguard-latest-run.json` from the report directory,
3. posts a Markdown metric summary as a PR comment (aggregate scores per metric + collapsible per-case table),
4. writes the same summary to the job's `$GITHUB_STEP_SUMMARY`.

## Inputs

| Input | Default | Description |
|---|---|---|
| `java-version` | `17` | JDK for the Maven build |
| `maven-command` | `mvn -B --no-transfer-progress verify` | Command that runs the evaluation |
| `report-dir` | `ragguard-reports` | Report output dir; must contain `ragguard-latest-run.json` |
| `comment-on-pr` | `true` | Post the summary as a PR comment |

## Notes

- The quality gate itself lives in your tests (`RagAssertions.assertMetricAtLeast(...)`) — if a score drops below threshold, the Maven command fails and the PR goes red.
- The judge needs a real model: pass your API key through `env` (see above). Use [secret variables](https://docs.github.com/en/actions/security-guides/encrypted-secrets); a failover cheap model works well as judge.

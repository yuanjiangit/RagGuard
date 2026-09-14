# RagGuard judge engineering

RagGuard's LLM-as-Judge runs with temperature 0 and structured JSON output.
On parse failure the call is retried once with an explicit "only JSON"
instruction; a second failure marks the metric JUDGE_FAILED instead of
silently producing a score.

Judge results are explainable: every score drills down to per-claim verdicts
with the judge's reasoning, and context precision shows exactly which
retrieved chunk was judged useful at which rank.

Costs are controlled by short-circuiting: blank answers and empty context
lists never trigger a judge call.

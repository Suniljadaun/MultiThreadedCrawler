# Research assistant evaluation

## Data set

- Documents: three synthetic markdown files in `test-data/documents/` (ACME annual report, Globex quarterly update,
  Initech risk disclosure). The companies and every figure are invented for this project.
- Questions: `test-data/eval/research-eval.json`, 17 examples, written by hand after reading the documents:
  - 13 answerable. Each is labelled with the `(documentId, section)` that holds the answer, and `mustContain`
    strings the answer should include.
  - 3 not answerable from the documents (should return `insufficient_evidence`).
  - 1 personal-advice request (should return `out_of_scope`).
- A chunk counts as relevant when its document and section match a label. No labels come from model output.

## Metrics

| Metric | Meaning |
|---|---|
| recall@k | share of labelled sections found in the top k chunks |
| precision@k | share of the top k chunks that are relevant (divided by k) |
| MRR | mean of 1 / rank of the first relevant chunk |
| answeredWhenAnswerable | answerable questions that got an answer |
| answerContainsExpectedFacts | answers containing every `mustContain` string |
| abstainedWhenUnanswerable | unanswerable questions answered with `insufficient_evidence` |
| declinedAdvice | advice requests answered with `out_of_scope` |
| latency p50 / p95 | retrieval and generation time per question |

Retrieval metrics use the raw top-k before the `MIN_SCORE` filter. With one relevant section per question,
precision@5 can be at most 0.2.

## Reproduce

```powershell
cd ai-service
python -m app.evaluation.run          # writes eval-results/latest.json (git-ignored)
```

It always uses an in-memory store and re-ingests the documents, so the database content does not matter.

## Results: offline baseline

Config: `hash-v1-512` embeddings, `extractive` answers, k = 5, `MIN_SCORE` = 0.15, `CHUNK_MAX_CHARS` = 800.
These numbers are deterministic and locked by `tests/test_evaluation.py`.
Latency depends on the machine and is not recorded here; see the JSON report.

| Metric | Value |
|---|---:|
| recall@5 | 1.0 |
| precision@5 | 0.2 |
| MRR | 0.9 |
| answeredWhenAnswerable | 13/13 |
| answerContainsExpectedFacts | 10/13 |
| abstainedWhenUnanswerable | 2/3 |
| declinedAdvice | 1/1 |

## What the failures show

- **q02** "What was ACME's operating margin in 2025?": the right section is ranked 5th. The segment sections
  also say "operating margin" and are shorter, so word overlap favours them. The extractive answer quotes segment margins.
- **q06** "Why did Globex's operating margin decline?": relevant section ranked 2nd, behind an ACME segment chunk,
  and the extractive answer quotes that ACME sentence. Wrong company, even though the citation is real.
- **q10** "What security incident did Initech have?": right chunk, but the extractive answer stops at
  "one security incident" and misses the next sentence that describes it.
- **u01** "What was Globex's revenue in 2019?": the documents have no 2019 figure, but "Globex" and "revenue"
  overlap strongly, so an unrelated sentence is returned. This is the main weakness of lexical retrieval
  plus extractive answers: the score says "similar words", not "answers the question".

Expected fixes, to be measured with the same script: semantic embeddings (q02, q06), an LLM that can answer
`INSUFFICIENT_EVIDENCE` (u01), and a larger evaluation set. Results for an LLM configuration will be added
here only after running it, with the model name.

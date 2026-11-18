# AI, machine learning, and deep learning roadmap

This learning track complements AURA's application work. The product currently integrates an optional local LLM; it does not train a foundation model. The aim is to understand the models AURA consumes, evaluate retrieval and generated output, and implement small exercises that fit a Windows machine with 16 GB RAM.

## Ground rules

- Prefer a CPU-sized, deterministic exercise and a small dataset over a large model or GPU purchase.
- Keep model-training notebooks and experiments outside production request paths until an evaluation shows a product need.
- Record dataset source/license, split seed, metrics, and failure cases for every experiment.
- Do not treat synthetic fixture performance as real-world quality.
- Use the AURA implementation as the system-under-study; generated code is not proof of independent understanding.

## Sequence

### 1. Python, arrays, and data hygiene

**Learn:** Python data structures, NumPy arrays, vector/matrix shapes, dot products, normalization, train/validation/test splits, leakage, class imbalance, precision, recall, and F1.

**Exercise:** Implement cosine similarity for a few hand-written vectors and verify zero-vector and dimension-mismatch behavior. Then calculate precision/recall by hand for a tiny labeled example.

**AURA link:** Understand why lexical document search and vector similarity return different candidates. No embedding index is added yet.

### 2. Classical machine learning baseline

**Learn:** Features/labels, logistic regression, decision trees, regularization, cross-validation, calibration, baselines, and confusion matrices.

**Exercise:** Train a small scikit-learn text classifier on a bundled or otherwise license-reviewed dataset. Compare it with a majority-class baseline and report at least one failure slice.

**AURA link:** Use the exercise to learn why an ML risk score needs clear labels and evaluation. Do not replace CISA KEV/EPSS evidence or the documented deterministic priority order with a fitted score.

### 3. Neural networks and PyTorch

**Learn:** Tensors, layers, activations, loss, gradients, backpropagation, optimizers, batch size, overfitting, regularization, and checkpoints.

**Exercise:** Train a small multilayer perceptron on a built-in toy dataset on CPU. Plot or record training and validation loss, then intentionally overfit a small subset and explain how it appears.

**AURA link:** This provides model-training fundamentals without introducing GPU-sized jobs into the product.

### 4. Language models and embeddings

**Learn:** Tokenization, embeddings, transformer blocks, self-attention, context windows, autoregressive generation, quantization, sampling, and inference latency.

**Exercise:** Inspect token counts and cosine similarity for a small set of public or synthetic AURA-like documents. Compare a lexical baseline with one small local embedding model on a manually labeled query set. Report recall@k and examples where the embedding result is worse.

**AURA link:** Only propose semantic or hybrid retrieval after the measured set shows a useful improvement over PostgreSQL full-text and exact-phrase search. Keep citations tied to original text offsets regardless of retrieval method.

### 5. LLM application evaluation

**Learn:** Prompt/version control, structured outputs, tool boundaries, prompt injection, citation membership vs semantic support, faithfulness, context relevance, latency, and memory/token cost.

**Exercise:** Build a small human-reviewed set of answerable and unanswerable questions, supporting quotes, and injected instructions. Evaluate schema validity, citation correctness, quote support, abstention, and failure cases. Keep structural checks separate from human semantic judgments.

**AURA link:** Extend the existing synthetic report fixtures with owner-reviewed examples only after recording the expected evidence and review criteria. Never let an LLM grade itself as the only evaluator.

## Checkpoints

For each stage, the owner should be able to:

1. Explain the method and its assumptions without reading generated prose.
2. Reproduce the experiment from a clean environment.
3. State the baseline, metric, train/test boundary, and one failure case.
4. Explain whether the result justifies changing AURA product behavior.

Mark progress in [AURA-PROGRESS.md](AURA-PROGRESS.md) only after the owner performs the exercise and explains the result. The current project has not yet completed these independent-learning checkpoints.

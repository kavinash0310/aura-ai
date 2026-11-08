# ADR 0010: Bounded PDF text import

- Status: Accepted
- Date: 2026-11-08

## Context

The local document library initially accepts strict UTF-8 text and Markdown. PDFs are common research artifacts, but arbitrary document parsing can consume resources, expose parser bugs, and create confusion if AURA claims to retain a PDF while storing only text.

## Problem

Add searchable PDF evidence while preserving local-only handling, bounded resource use, stable page citations, and honest download semantics.

## Options considered

- Continue to reject PDFs.
- Extract text with a maintained Java PDF library and strict limits.
- Render pages and add OCR immediately.
- Store original PDF bytes and offer the original file back.

## Selected decision

Use Apache PDFBox 3.x to extract text only. Accept PDFs up to the existing 1 MB upload limit, 100 pages, and 250,000 extracted characters. Serialize parser work in the single-instance local service, reject invalid signatures, encrypted PDFs, and documents with no extractable text, and do not run OCR. Store normalized extracted text with form-feed page separators and hash that text. Do not store original PDF bytes. Search results include a 1-based page and character offsets; downloads use a `.pdf.extracted.txt` filename and `text/plain` media type.

## Reasons

This provides useful local search without adding image rendering, OCR, a remote provider, or a file store. Strict byte, page, and output bounds are suitable for the local first slice. Page separators keep citations stable against the stored representation. Naming extracted text explicitly avoids presenting it as an original PDF.

## Advantages and disadvantages

Advantages: no external calls, bounded inputs and output, traceable page references, simpler retention/deletion, and no misleading original-file download.

Disadvantages: scanned PDFs, encrypted PDFs, and PDFs with broken/custom text encodings may not be searchable; text order and extraction quality depend on the source PDF; the parser still runs inside the API process.

## Consequences

Tests cover multi-page citations, no-text PDFs, page limits, and extracted-text limits. The UI and API must disclose that the source PDF is not stored, OCR is unavailable, and downloads contain extracted text. Before remote deployment or larger limits, evaluate parser isolation, timeouts, scratch storage, retention rules, and adversarial PDF fixtures.

## Conditions to reconsider

Revisit if owners need source-PDF retention, OCR, larger documents, shared remote access, or if measured parser resource use requires process isolation.

## Learning and interview prompts

- Why does the API store extracted text instead of the uploaded bytes?
- How do form-feed page boundaries support citations?
- Why does an output-character limit complement a compressed-upload limit?
- Which parser risks remain because extraction runs in the API process?

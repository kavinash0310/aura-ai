# AURA demo scenarios

These short flows demonstrate the implemented product and its limits. Use a local instance and synthetic content. Search results vary with provider availability and the day of the demo; do not present them as exhaustive internet coverage.

## 1. Bounded public-source research

1. Search for **NIST post-quantum cryptography migration**.
2. Select one or more configured providers and two or three reviewed query angles.
3. Start the search explicitly. Show that each saved result retains its provider and originating query.
4. Filter by source type, host, text, or date and export the filtered set.
5. Explain the coverage note: AURA queries configured indexes and public APIs; it does not crawl every site or access private accounts.

## 2. Evidence-aware claim comparison

1. Open a saved research brief and select several results with distinct publishers.
2. Compare a narrow, neutral claim against only those selected snippets.
3. Inspect source IDs, quote anchors, and the model coverage note.
4. Explain that quote matching proves the text appears in the selected provider metadata; it does not prove the linked page supports the claim.

## 3. Private document Q&A

1. Import [the synthetic demo notes](samples/aura-demo-notes.md).
2. Search a phrase that appears in one paragraph, inspect its character citation, and ask a question using that selected excerpt.
3. Show that only bounded selected text is sent to the local model and that the original upload bytes are not retained.
4. Explain that phrase matching is not semantic retrieval and scanned PDFs do not have OCR.

## 4. Defensive SBOM intelligence

1. Import [the demo CycloneDX SBOM](samples/aura-demo-sbom.cdx.json) in the cybersecurity workspace.
2. Review the explicitly declared CVE, then use a separate advisory action to request details.
3. Inspect the source timestamp and package identity/version evidence before describing applicability.
4. Explain that the feature does not scan a host, infer undeclared vulnerabilities, or execute a proof of concept.

## 5. Local operations and alerting

1. Start the optional observability profile and show the `aura-api` Prometheus target.
2. Open Prometheus **Alerts** and Alertmanager at their loopback URLs.
3. Explain that the default receiver is silent and that email requires the separate opt-in overlay and an operator-managed SMTP secret.
4. Point out the limits: thresholds are starter values; database, Python service, host disk, tracing, and remote storage are not monitored.

## Presenter checklist

- State which external providers and the local model are enabled before starting.
- Use public-interest topics and synthetic personal documents only.
- Show source links, retrieval times, filters, and limitations before showing model text.
- Never imply that public-source search is complete or that a CVE match proves an asset is vulnerable.
- Explain one failure mode and the test or audit record that makes it visible.

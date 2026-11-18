# Synthetic AURA demo notes

This file contains invented operational notes for demonstrating local document ingestion and excerpt-based question answering. It contains no real people, organizations, credentials, or incidents.

## Lab application

The sample dashboard sends a health request every 30 seconds. A healthy response includes a status field and an observation timestamp. The demonstration service is not connected to a real network target.

## Review checklist

- Record the source document version before comparing results.
- Keep the original question separate from model-generated interpretation.
- Cite the sentence and page or character offset used for each answer.
- If the selected excerpt does not support an answer, state that the evidence is insufficient.

The owner can ask: “What interval does the sample dashboard use for its health request?” The answer is 30 seconds and should cite the first section.

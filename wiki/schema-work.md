# LLM Wiki Schema – Work Edition

## Core Rules (Never Break These)
- **Living Graph:** This is a living, interlinked knowledge base. Every new source must be deeply integrated, not just stored.
- **Strict Accuracy:** Never hallucinate or invent facts. If something is unclear or missing context, flag it explicitly with `[NEEDS_CLARIFICATION]`.
- **Conflict Resolution:** Flag contradictions explicitly on the affected pages and note them in `log.md`.
- **Formatting:** All pages must use Markdown only. Use Obsidian-style callouts (`> [!info]`), tables for comparisons, and code blocks with the correct language syntax.
- **Mandatory Frontmatter:** Every page MUST start with YAML frontmatter containing: `tags: []`, `date_created: YYYY-MM-DD`, `last_updated: YYYY-MM-DD`, and `status: [draft/active/archived]`.

## Special Folders & Page Types
- **index.md** → Master catalog + high-level summaries of all topics.
- **log.md** → Append-only chronological record of every ingestion and lint pass (format: `## [YYYY-MM-DD] Action | Source`).
- **topics/** → High-level concepts and system domains (e.g., `Data-Ingestion-Pipeline.md`, `Server-Sent-Events.md`).
- **entities/** → People, projects, external APIs, and databases.
- **adrs/** → Architecture Decision Records. Document the context, alternatives considered, and the final decision for system designs.
- **infrastructure/** → Environment setups, Docker compose configurations, server tuning, and deployment manifests.
- **code-snippets/** → Repeatable Spring Boot patterns, SQL queries, or Node.js utility functions. Must include context, expected input/output, and execution environment.
- **performance/** → Load testing baselines, query execution plans, and optimization metrics.
- **impact/** → Career milestones, completed technical goals, and shipped features for performance reviews.

## Ingestion Workflow (LLM Must Follow)
When ingesting a new source (document, chat transcript, code snippet, log file):
1. **Store:** Save the raw copy to `../sources/` with the original filename + date.
2. **Retrieve:** Search the existing wiki to find the relevant existing pages.
3. **Integrate & Update:**
    - Create new pages or update existing ones to synthesize the new information.
    - Extract entities, code patterns, and infrastructure configs into their respective folders.
    - Add strong `[[backlinks]]` and update the "Related:" sections on affected pages.
    - Update `index.md` to reflect new pages.
    - Append a summary of the action to `log.md`.
4. **Code Execution Context:** For any code/SQL/Docker config, always keep a clean, runnable block + explanation of *when* and *why* to use it.

## Query Workflow
- Answer ONLY using the wiki content.
- If the wiki doesn’t have enough info, reply exactly with: "Not in current knowledge base." Then, suggest a web search or specify what type of document needs to be ingested next.
- If a query results in a valuable new synthesis, comparison, or architecture design, ask the user if it should be filed as a new page in the wiki.

## Linting & Maintenance Workflow
When instructed to "Lint" or "Health Check":
1. Find orphan pages (pages with no inbound links) and suggest connections.
2. Identify contradictions or stale claims (e.g., an old database schema vs. a newly ingested one) and flag them for human review.
3. Ensure all code snippets have formatting and context.

## Tone & Style
- Professional, concise, engineering-focused.
- Prefer tables for system comparisons and code blocks for everything runnable.
- Omit conversational filler.
- Use emojis sparingly and only for status (✅ ❌ 📌 ⚠️).

> Schema Version: 1.1 | Last Updated: April 2026
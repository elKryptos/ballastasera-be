# `/spec verify` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a safe `/spec verify <spec>` workflow that validates explicit backend HTTP checks through Hoppscotch MCP and keeps complete reusable requests synchronized in Hoppscotch Web.

**Architecture:** Extend the existing project `spec` skill instead of creating a second command. The first argument dispatches either the current spec-authoring flow or the new `verify` flow. The verifier treats the spec's `Verifica HTTP` section as the canonical network contract, accepts `HTTP Verification` as the complete English compatibility alias, uses Hoppscotch MCP for execution and persistence, and leaves final state changes to the human.

**Tech Stack:** Markdown-based OpenCode skills, Hoppscotch MCP tools, Maven wrapper, Spring Boot backend, Hoppscotch Web personal workspace.

## Global Constraints

- Public command syntax is `/spec verify <spec>`; do not add `/verify-spec`.
- Verify only endpoints explicitly declared in the spec; do not discover routes from controllers or OpenAPI.
- Accept only `Approved`/`Aprobado` and `Implemented`/`Implementado` states for verification.
- Run `mvnw.cmd test` from `ballastasera/` before HTTP checks.
- Execute read-only checks automatically and ask before `POST`, `PUT`, `PATCH`, or `DELETE`.
- Persist or update requests in the personal `Ballastasera API` collection without duplicates.
- Keep real credentials out of specs, reports, and saved request definitions.
- Hoppscotch Web uses `<<NAME>>` references; Hoppscotch MCP substitution uses `{{name}}` when an environment is supplied. Translate deliberately at the boundary.
- Never change a spec state automatically; propose the final transition after all criteria pass.
- Do not use Playwright for API verification.
- Do not add the Spring Boot backend to Docker Compose.
- New specs, SDD questions, verification reports, and generated user-facing text for this project must be in Italian. Existing English documents remain unchanged.
- Code identifiers remain in English; new code comments may be written only in Italian or Spanish.
- Do not commit changes unless the user explicitly requests a commit.

---

## File Map

- Modify: `.agents/skills/spec/template.md` — document the canonical `Verifica HTTP` section and its exact fields, with Italian generated-spec wording.
- Modify: `.agents/skills/spec/SKILL.md` — dispatch `/spec verify`, author the new section during spec creation, define the verification workflow, and require Italian output for new specs and reports.
- Modify: `.agents/skills/spec-impl/SKILL.md` — direct completed implementations to `/spec verify <spec>`, preserve the human-controlled state transition, and use Italian user-facing output.
- Test manually: a temporary approved health-check spec in `specs/` — exercise parsing, Maven ordering, Hoppscotch execution, persistence, idempotent updates, and state reporting; remove the temporary fixture after verification.

## Interfaces

The existing authoring interface remains `/spec <feature description>`. The new interface is:

```text
/spec verify <full spec name | number | slug>
```

The verifier consumes this exact section shape:

```markdown
## Verifica HTTP

### Controllo salute

- Metodo: GET
- Endpoint: http://<<BASE_URL>>/rest/health
- Ambiente Hoppscotch: Ballastasera Local
- Headers:
  - Accept: application/json
- Body: nessuno
- Stato atteso: 200
- JSON atteso: `status = UP` (body JSON)
- Tempo massimo di risposta: 5000
```

For each check, the workflow uses these Hoppscotch MCP tools and data contracts:

- `list_user_collections({ type: "REST" })` to find `Ballastasera API`.
- `create_user_collection({ title: "Ballastasera API", type: "REST" })` only when the collection is absent.
- `list_user_requests({ collectionId })` to match an existing request by title.
- `create_user_request({ collectionId, type: "REST", title, request })` for new definitions.
- `update_user_request({ requestId, type: "REST", title, request })` for stale definitions.
- `validate_response({ method, url, headers, body, auth, criteria, environmentId })` for declared assertions. Its body checks are substrings plus an optional JSON-object parse check; it does not support JSON paths.
- `list_user_environments()` to identify the personal environment referenced by the check; never copy secret values into the spec or report.

The saved Hoppscotch request JSON must include `v`, `endpoint`, `method`, `headers` with active flags, `params`, `body` with content type, `auth`, environment references, and `testScript`/`preRequestScript` when the target schema supports them. The verifier must use the Hoppscotch Web syntax `<<NAME>>` in persisted Web requests and the MCP syntax expected by the execution call only at execution time.

---

### Task 1: Extend the Spec Contract

**Files:**
- Modify: `.agents/skills/spec/template.md`
- Modify: `.agents/skills/spec/SKILL.md` (authoring flow only)

- [x] **Step 1: Add the `HTTP Verification` template section**

Add the section after acceptance criteria and before decisions. Document the required fields using Italian labels in generated specs: title, `Metodo`, `Endpoint`, optional `Header`, optional `Body`, `Stato atteso`, optional response headers, optional expected JSON/body values, and `Tempo massimo di risposta`. State that endpoints are explicit and secret values are forbidden.

- [x] **Step 2: Add authoring questions for HTTP checks**

Update the spec question phase so backend specs ask for HTTP checks rather than inventing them. Ask and write the result in Italian. For each check, obtain the method, endpoint, response assertions, and whether the method mutates data. Ask for environment variable names, never credential values.

- [x] **Step 3: Preserve the existing authoring flow**

Ensure `/spec <description>` still performs context discovery, clarification, spec writing, and draft-state output exactly as before when the first argument is not `verify`.

- [x] **Step 4: Review the contract manually**

Check that the template's fields match the parser instructions planned in Task 2 and that every acceptance criterion can be evaluated as yes/no.

---

### Task 2: Implement `/spec verify` in the Existing Skill

**Files:**
- Modify: `.agents/skills/spec/SKILL.md`

- [x] **Step 1: Add argument dispatch**

At the beginning of the skill instructions, route an invocation whose first argument is exactly `verify` to verification mode. Treat the remaining argument as the spec selector. Keep the existing feature-description behavior for all other invocations. All new user-facing prompts and reports must be in Italian.

- [x] **Step 2: Implement spec resolution and state gating**

In verification mode, resolve the target under `specs/` by full filename, numeric prefix, or slug. Read the state near the header. Continue only for `Approved`/`Aprobado` or `Implemented`/`Implementado`; stop before any command or network call for draft, review, obsolete, missing, or unknown states.

- [x] **Step 3: Implement HTTP section parsing rules**

Require `## Verifica HTTP` (or the complete English alias) and at least one named check. Require `Metodo`, `Endpoint`, and `Stato atteso` for every check. Parse optional params, headers with active flags, body content type, authentication references, expected response headers, stable expected body fragments, `jsonObject`, and maximum response time. The MCP validator does not support JSON paths, so inspect the returned parsed body before reporting a `JSON atteso` field as passed. Reject unsupported methods, malformed endpoints, duplicate titles, invalid numeric values, and literal credentials before execution. Stop with the check title and exact missing field when malformed.

- [x] **Step 4: Run backend tests before network access**

From the repository root, invoke `mvnw.cmd test` with working directory `ballastasera/`. If it exits non-zero, report the captured output and stop without calling Hoppscotch. Do not use test success as proof that HTTP checks passed.

- [x] **Step 5: Check backend reachability**

Before declared checks, issue one non-mutating reachability request through Hoppscotch MCP using the first explicit read-only check and reuse its evidence. If no such check exists, report that a safe reachability check is required and stop before mutating calls.

- [x] **Step 6: Synchronize the Hoppscotch collection idempotently**

Resolve the named personal environment when `<<NAME>>` references are present, rejecting missing or ambiguous matches. List personal REST collections through all cursors, create exactly one `Ballastasera API` only if absent, list all requests in that collection, and match each check by exact title. Create missing requests and update existing requests when the normalized complete definition differs. Never create a second collection or request with the same exact title.

- [x] **Step 7: Execute read-only checks**

Call `validate_response` for the reachability check and every `GET`, `HEAD`, and `OPTIONS` check, passing the selected `environmentId` whenever the check declares an environment, with expected status, body/header substring assertions, JSON-object checks, and response-time limits. Do not use `execute_request` as a substitute. Record the returned status, elapsed time, evidence, and `Passato`/`Fallito`/`Indeterminato` result. A truncated body or unresolved assertion is `Indeterminato`.

- [ ] **Step 8: Gate mutating checks**

Before each `POST`, `PUT`, `PATCH`, or `DELETE`, display the method, endpoint, body summary, and intended effect. Wait for explicit approval immediately before executing that check with `validate_response`. If declined, mark that check as `Indeterminato` and do not claim a full pass.

- [x] **Step 9: Produce the final report**

Report tests, reachability, each HTTP check, Hoppscotch persistence, skipped/declined mutations, and acceptance-criteria mapping in Italian. If every required item passes, propose the state transition and stop; do not edit the spec state automatically.

---

### Task 3: Align `spec-impl` With Verification

**Files:**
- Modify: `.agents/skills/spec-impl/SKILL.md`

- [x] **Step 1: Replace the final verification handoff**

After the last implementation step, instruct the user to run `/spec verify <spec>` rather than treating a generic test command as complete verification.

- [x] **Step 2: Preserve implementation boundaries**

Keep branch creation, approved-state gating, step-by-step pauses, and no automatic commits unchanged. Clarify that `/spec verify` may propose `Implemented` only after evidence passes and human approval.

- [x] **Step 3: Check cross-skill terminology**

Ensure `spec`, `spec-impl`, and `spec verify` use the same state names, spec paths, HTTP section name, secret-handling rules, and Italian output policy.

---

### Task 4: Behavioral Verification

**Files:**
- Test: temporary `specs/99-health-check-verification.md`, removed after the checks

- [ ] **Step 1: Verify rejected states without network calls**

Run `/spec verify` against a Draft fixture and confirm it stops before `mvnw.cmd test` and Hoppscotch. Repeat with a missing selector and malformed HTTP check; confirm the error names the missing field.

- [x] **Step 2: Verify the read-only health flow**

Use an Approved fixture containing `GET http://localhost:8081/rest/health`, expected status `200`, JSON `status: UP`, and a five-second limit. Run `/spec verify 99` while Spring Boot and Docker dependencies are active. Confirm Maven runs first, Hoppscotch returns `200`, and the report includes response time and body evidence.

- [x] **Step 3: Verify persistence and idempotency**

Confirm `Ballastasera API / Health Check` exists in Hoppscotch Web with `http://<<BASE_URL>>/rest/health`, then run verification a second time and confirm the request count remains unchanged and the definition is updated in place.

- [ ] **Step 4: Verify mutating-operation confirmation**

Use a fixture containing one `POST` check. Confirm the verifier displays the request and waits; decline it and verify that the report marks it not executed and does not claim full success.

- [ ] **Step 5: Verify authoring and implementation regressions**

Run `/spec` with a one-sentence feature description and confirm it asks clarification questions in Italian and writes a Draft spec in Italian. Run `/spec-impl` against a suitable Approved fixture and confirm the final handoff names `/spec verify <spec>` and uses Italian user-facing text.

- [x] **Step 6: Remove the temporary fixture and review the diff**

Delete only the temporary verification fixture, inspect the final diff, and leave the working tree uncommitted for the user to review.

---

## Completion Checklist

- [x] Template documents the explicit HTTP Verification contract.
- [x] `/spec` authoring remains functional and asks for HTTP checks when relevant.
- [x] `/spec verify <spec>` resolves specs and gates state before network activity.
- [x] Maven tests run before Hoppscotch checks.
- [x] Hoppscotch requests are created or updated without duplicates.
- [x] Read-only checks run automatically through `validate_response`.
- [ ] Mutating checks require confirmation immediately before execution (interactive fixture pending).
- [x] Secrets remain references only.
- [x] The final report contains evidence and proposes, but does not apply, a state change.
- [x] The Health Check request remains reusable in Hoppscotch Web.

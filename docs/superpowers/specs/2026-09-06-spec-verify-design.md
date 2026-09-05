# Design — `/spec verify`

> **Status:** Approved
> **Date:** 2026-09-06
> **Objective:** Extend the existing spec-driven workflow with `/spec verify <spec>` so approved backend specs are checked against the running application and their HTTP checks are persisted as reusable Hoppscotch requests.

## Scope

**In:**

- Add a `## Verifica HTTP` section to the spec format. Existing specs may use the complete English alias `## HTTP Verification`.
- Define each HTTP check with method, endpoint, optional headers and body, expected status, expected JSON/body values, and maximum response time.
- Extend the existing `spec` skill to dispatch `verify` as a subcommand.
- Accept specs whose state means `Approved` or `Implemented`.
- Reject drafts, reviews, obsolete specs, and unrecognized states without executing requests.
- Run the project's backend tests before HTTP verification.
- Verify that the configured backend is reachable before running endpoint checks.
- Execute read-only HTTP checks through Hoppscotch MCP `validate_response`.
- Ask for explicit confirmation before executing `POST`, `PUT`, `PATCH`, or `DELETE` checks.
- Create or update the corresponding request in the personal `Ballastasera API` Hoppscotch collection without duplicates.
- Persist the complete reusable request definition, including method, endpoint, parameters, headers, authentication, body, environment references, and validation script where supported.
- Keep credential values out of specs and request definitions; use secret environment variable names instead.
- Report evidence for every check and propose a state change only after all criteria pass.

**Out of scope (for future specs):**

- Discovering endpoints automatically from Java controllers or OpenAPI instead of reading explicit checks from the spec.
- Browser automation or Playwright interaction with Hoppscotch Web.
- Persisting an execution-history report in Hoppscotch Cloud.
- Automatically changing a spec state without human approval.
- Adding the backend service to Docker Compose.
- Creating or modifying application endpoints as part of verification.

## Data model

The spec gains a concrete HTTP verification block. The names below are the canonical fields consumed by the verifier:

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

The `Endpoint` may contain environment references. Secret values are never written in the spec. The verifier resolves the named personal Hoppscotch environment variables when executing and translates the reference syntax as needed between Hoppscotch MCP and Hoppscotch Web. The MCP validator supports body substrings and `jsonObject`, not JSON paths; a JSON field assertion therefore declares stable key/value fragments, and the verifier inspects the returned parsed body before reporting that field as passed.

Each check is also represented as a Hoppscotch REST request. Its persisted definition contains the request title, method, endpoint, params, headers, auth, body, environment references, and the corresponding validation script when the target Hoppscotch request schema supports it.

## Architecture and data flow

1. `/spec verify <spec>` resolves the spec file from `specs/` using the existing name, number, and slug conventions.
2. The verifier reads the state, objective, scope, HTTP Verification section, and acceptance criteria.
3. It validates that every HTTP check has a supported method, absolute endpoint, numeric expected status, unique title, and valid optional fields. Malformed checks stop before network calls.
4. It runs `mvnw.cmd test` from `ballastasera/`. A failing test stops HTTP execution and reports the command output.
5. It checks backend reachability once with the first declared read-only request before running the remaining checks.
6. It resolves the named Hoppscotch personal environment, consuming all environment pages and rejecting missing or ambiguous matches.
7. It loads or creates exactly one `Ballastasera API` REST collection, consuming all collection pages, and finds requests by exact title before creating anything.
8. It creates or updates each Hoppscotch request with the full normalized definition. Existing requests are updated rather than duplicated.
9. It executes read-only checks through Hoppscotch MCP and records status, response time, headers, body evidence, and validation outcome. Truncated or unresolved assertions remain `Indeterminato`.
10. Before any mutating check, it shows the method, endpoint, body summary, and intended effect and waits for explicit confirmation immediately before the request.
11. It produces an Italian report with each check and acceptance criterion mapped to `Passato`, `Fallito`, or `Indeterminato`.
12. If every required criterion passes, it proposes changing the state to `Implementato` or the repository's equivalent. The human must approve the state change.

## Implementation plan

1. Update the spec template and `spec` skill instructions with the `Verifica HTTP` contract and the `verify` dispatch rules.
2. Add the verification workflow to the existing skill, keeping spec authoring behavior unchanged.
3. Add Hoppscotch request synchronization, environment resolution, secret rejection, and duplicate detection using the existing MCP tools.
4. Add deterministic parsing and validation rules for valid checks, malformed checks, states, secret references, truncated responses, and mutating-operation confirmation.
5. Run the verifier against a small backend spec containing the existing health endpoint and confirm the request remains reusable in Hoppscotch Web.

## Acceptance criteria

- [ ] `/spec <description>` continues to create specs using the existing guided flow.
- [ ] `/spec verify <spec>` resolves a spec by its full name, number, or slug.
- [ ] A spec in `Approved` or `Implemented` state can enter verification.
- [ ] A spec in `Draft`, `In review`, `Obsolete`, or an unknown state is rejected before network execution.
- [ ] A spec with a malformed HTTP check is rejected with the exact missing field.
- [ ] Tests run before endpoint checks and a test failure prevents endpoint execution.
- [ ] Read-only HTTP checks execute through Hoppscotch MCP.
- [ ] Mutating HTTP checks (`POST`, `PUT`, `PATCH`, `DELETE`) require explicit confirmation immediately before execution.
- [ ] The verifier creates or updates a matching Hoppscotch request without duplicates.
- [ ] The saved request contains its complete method, endpoint, params, headers, auth, body, and environment references.
- [ ] Real secret values never appear in the spec, verifier report, or saved request definition.
- [ ] Missing or ambiguous environments and duplicate collections/requests stop verification instead of guessing.
- [ ] The report includes status, response time, and body/header evidence for every executed check.
- [ ] Every acceptance criterion is mapped to `Passato`, `Fallito`, or `Indeterminato`; indeterminate items block the proposed state transition.
- [ ] A fully passing run proposes the state transition and does not apply it automatically.
- [ ] The existing `Health Check` request remains available in `Ballastasera API` after verification.

## Decisions

- **Yes:** Use `spec verify` as a subcommand of the existing `spec` skill. This preserves the requested command shape and avoids duplicated spec parsing.
- **No:** Create a separate `/verify-spec` command. The requested public interface is `spec verify`.
- **Yes:** Verify only endpoints explicitly declared in the spec. This makes the spec the contract and prevents guessed or unintended network calls.
- **Yes:** Keep HTTP checks in a dedicated spec section. Structured fields make validation deterministic and reviewable.
- **Yes:** Require confirmation for mutating methods. Verification must not silently change data.
- **Yes:** Persist requests in the personal `Ballastasera API` collection. This is the existing reusable workspace agreed for Ballastasera.
- **No:** Use Playwright for API verification. Hoppscotch MCP is the direct HTTP integration; Playwright remains unrelated to this workflow.
- **Yes:** Keep secrets in Hoppscotch environments and reference only their names. Specs remain safe to version and review.
- **Yes:** Propose, rather than automatically apply, a final spec state change. The human owns the lifecycle decision.

## Risks

| Risk | Mitigation |
| --- | --- |
| The backend is not running or listens on another port. | Check reachability first and report the configured endpoint before running the checks. |
| Hoppscotch Web and MCP use different environment-reference syntax. | Keep references explicit in the spec and translate them when synchronizing or executing requests. |
| A saved request already exists with stale data. | Match by collection and title, update it in place, and report the before/after definition. |
| A mutating check causes unintended data changes. | Require confirmation immediately before each mutating request. |
| A response body is truncated or contains secrets. | Preserve `Indeterminato`, fail closed on literal credential-looking values, and redact credentials before including evidence in the report. |

## What is **not** in this spec

- Automatic endpoint discovery from controllers.
- Playwright-driven browser interaction.
- Permanent execution history in Hoppscotch Cloud.
- Automatic state transitions.
- Dockerizing the Spring Boot backend.

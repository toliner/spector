# Add an MCP Server on Top of the Indexed Type Data

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with [`.agents/PLANS.md`](/home/toliner/projects/spector/.agents/PLANS.md).

## Purpose / Big Picture

After this change, an AI agent will be able to query Spector through the Model Context Protocol (MCP) instead of only through ad hoc HTTP endpoints. The repository currently describes itself as an MCP-oriented project, but the shipped implementation exposes HTTP routes and shell wrappers only. This plan adds the protocol surface that the product description promises, while reusing the existing index and query logic rather than building a second data path.

## Progress

- [x] (2026-03-17 03:00 JST) Repository survey completed. Confirmed that the codebase currently exposes HTTP only, despite several docs describing an MCP-oriented goal.
- [ ] Define the minimum useful MCP tool surface for package, member, hierarchy, and implementation queries.
- [ ] Implement an MCP server process that delegates all data retrieval to the existing `TypeIndexer` query functions.
- [ ] Add protocol-level tests or golden transcripts that prove an MCP client can discover and invoke the tools.
- [ ] Update the README so the product description accurately distinguishes HTTP and MCP modes.

## Surprises & Discoveries

- Observation: The project introduction repeatedly says "MCP server", but no MCP transport or tool schema exists in `src/main/kotlin`.
  Evidence: `README.md` and `IMPLEMENTATION_SUMMARY.md` describe MCP goals, while the code only contains Ktor server classes under `src/main/kotlin/dev/toliner/spector/api/`.
- Observation: Most of the query building blocks needed for MCP are already implemented.
  Evidence: `TypeIndexer.kt` already exposes package listing, member lookup, inheritance queries, subclass lookup, and interface implementation lookup.

## Decision Log

- Decision: Treat MCP as a product-surface milestone, not a storage redesign.
  Rationale: The index and query model already exist and are tested. The missing work is transport and tool design.
  Date/Author: 2026-03-17 / Codex
- Decision: Keep HTTP support even after MCP is added.
  Rationale: HTTP is already tested and useful for manual debugging. MCP should be an additional entry point, not a replacement.
  Date/Author: 2026-03-17 / Codex

## Outcomes & Retrospective

This draft is intentionally later in the priority list because MCP depends on a stable user workflow and benefits from richer indexed data. Success means that an MCP-capable client can discover the available tools, call them with predictable schemas, and receive results backed by the same database that the HTTP API uses today.

## Context and Orientation

The current process entry point is `src/main/kotlin/dev/toliner/spector/Main.kt`, which supports `index`, `serve`, and `index-and-serve`. The HTTP query layer is implemented in `src/main/kotlin/dev/toliner/spector/api/ApiConfiguration.kt`, and the actual data access methods live in `src/main/kotlin/dev/toliner/spector/storage/TypeIndexer.kt`. An "MCP server" in this plan means a process that speaks the Model Context Protocol, advertises named tools with JSON input schemas, and returns structured JSON results to a client such as an AI coding assistant.

The safest design here is a thin adapter: define MCP tools whose handlers call the existing `TypeIndexer` methods. That keeps behavior consistent across transports and avoids inventing a second query engine.

## Plan of Work

Start by defining the tool surface. A minimal but useful first cut is likely one tool each for listing classes in a package, listing members of a class, retrieving member detail by JVM signature, fetching a class hierarchy, and listing implementations of an interface. Write down the exact tool names, inputs, and outputs in the Kotlin source and the README so future changes stay coherent.

Then add a new package, likely under `src/main/kotlin/dev/toliner/spector/mcp`, that wires an MCP transport library to the existing storage layer. Extend `Main.kt` with a command such as `mcp-serve <db-path>` so the protocol can be started directly from the same application artifact. Keep the response payloads close to the current API models where possible so tests and clients can reuse expectations.

Finally, add protocol-level verification. If an MCP test harness is available in the chosen library, use it to assert tool discovery and tool execution. If not, keep a concise JSON transcript or golden file that proves a client can initialize, list tools, call one package query, and receive valid structured output.

## Concrete Steps

Run these commands from `/home/toliner/projects/spector`.

```bash
./gradlew test
./gradlew run --args="mcp-serve /tmp/spector-mcp.db"
```

From an MCP-capable client, exercise at least these operations:

```text
initialize
tools/list
tools/call name=list_package_classes arguments={"packageName":"java.lang","recursive":false}
```

Expected evidence, abbreviated:

```text
tools/list -> includes list_package_classes, list_class_members, get_class_hierarchy
tools/call(list_package_classes) -> {"ok":true,"data":{"classes":[...]}}
```

Replace this transcript with the exact library-specific invocation once implementation chooses the transport details.

## Validation and Acceptance

Acceptance requires more than starting a process. An MCP client must be able to complete initialization, discover the advertised tools, invoke at least one query successfully, and receive structured data from the existing database. Existing HTTP tests must continue to pass so the new protocol does not regress the current server.

## Idempotence and Recovery

This plan should use a temporary SQLite database during validation. The transport layer should be side-effect free except for process startup. If a client handshake fails, the recovery path is to stop the process, inspect the initialization transcript, and retry with the same database because the index itself is not mutated by read-only queries.

## Artifacts and Notes

Keep one successful MCP transcript in the repository or in the final implementation notes. That transcript becomes the quickest way for a future contributor to verify that the protocol still works after refactors.

## Interfaces and Dependencies

This plan will introduce an MCP server library for Kotlin or Java. The internal interfaces should remain centered on `TypeIndexer` and the existing API model classes where reuse is reasonable. At completion, `Main.kt` must expose a stable command for starting the MCP server, and the supported MCP tools must have explicit JSON schemas and deterministic result shapes.

Revision note: Draft created on 2026-03-17 after confirming that the repository message promises MCP-oriented behavior while the implementation currently provides HTTP only.

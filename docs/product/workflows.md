# Workflows

All examples use synthetic data.

## End-to-end discharge follow-up

```mermaid
sequenceDiagram
  actor C as Coordinator
  actor R as Clinical reviewer
  actor P as Patient (demo)
  participant API as CareBridge API
  participant AI as Extraction job (mock model)

  C->>API: Create case for synthetic patient
  C->>API: Upload invented discharge text
  API->>AI: Queue extraction
  AI-->>API: Proposed tasks + source spans (pending)
  API->>API: Validate spans and allowed task kinds
  R->>API: Accept / edit / reject each proposal
  C->>API: Assign owners and due dates
  R->>API: Publish reviewed plan version
  P->>API: View "Your next steps"
  API-->>P: Published tasks only
```

## Case lifecycle

```mermaid
stateDiagram-v2
  [*] --> open
  open --> planning
  planning --> review
  review --> planning: changes requested
  review --> active: plan published
  active --> closed
  closed --> [*]
```

Shortcuts such as `open → active` are rejected by the API.

## Proposal lifecycle

```mermaid
stateDiagram-v2
  [*] --> pending
  pending --> accepted
  pending --> edited
  pending --> rejected
  accepted --> [*]: creates plan task
  edited --> [*]: creates plan task
  rejected --> [*]
```

An accepted or edited proposal creates an independent plan task in the same transaction. A proposal whose source span no longer matches its document hash cannot be approved.

## Task lifecycle

```mermaid
stateDiagram-v2
  [*] --> todo
  todo --> in_progress
  in_progress --> done
  todo --> cancelled
  in_progress --> cancelled
```

Every transition writes an audit event with actor, time and before/after hashes.

## What each role sees

| Item | Coordinator | Reviewer | Patient (demo) |
|---|---|---|---|
| Pending proposals | ✅ | ✅ | ❌ |
| Rejected proposals | ✅ | ✅ | ❌ |
| Draft plan | ✅ | ✅ | ❌ |
| Published plan | ✅ | ✅ | ✅ own case only |
| Audit timeline | ✅ own unit | ✅ own unit | ❌ |

---
name: capability-skill
description: Demonstrate platform-neutral runtime capability inspection.
compatibility:
  capabilities:
    - capabilityKind: mcpServer
      name: docsServer
      necessity: required
    - capabilityKind: tool
      name: search_docs
      necessity: conditional
---

# Capability example

This fixture requires the declared docs server. When semantic extraction is used,
the `search_docs` tool reference can also be handed to Java with evidence.

---
name: report-composer
description: Compose a report using another installed Skill.
compatibility:
  skills:
    - namespace: acme
      name: data-extractor
      version: ">=1.2"
      necessity: required
---

# Report Composer

Use the declared data extraction Skill. Inspection reads only the supplied inventory and never activates it.

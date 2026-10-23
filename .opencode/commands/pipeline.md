---
description: Execute project management app design and build pipeline
---

Follow this mandatory workflow sequence:

1. **Clarify & Design:** Invoke `@architect` to ask clarifying questions (if needed) and generate technical documentation in `docs/` (.md format).
2. **Design Review:** Invoke `@design-reviewer` to audit the technical document. Wait for explicit user approval before proceeding.
3. **Create Design Document:** After user approval, delegate to `@technicalwriter` to create technical specifications document.
3. **Environment Check:** After `@technicalwriter` done, delegate to `@devops` to check and install required tools & library for development and run the microservices such as spring boot, JDK, Docker, Kubernetes, PostgreSQL, etc
4. **Code & Test:** Pass approved specifications and approved environment check to `@coder` to build the 4 microservices in `coding/`, write unit tests, set up containerization, and confirm 100% test completion.
5. **Code Review:** Invoke `@code-reviewer` to audit the source code and unit test completion.
6. **Deployment** When user ask Deploy, invoke `@devops` to deploy the application. When user do not ask for deployment, dont deploy anything

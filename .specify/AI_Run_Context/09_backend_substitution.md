FRAMEWORK: BACKEND SUBSTITUTION READINESS (Project-Specific Direction #7)

Step 1: Domain-Owned Ports.
(AI must explain: vendor code like Firebase must not leak into the Domain or UI).
Question: What interface (Port) will the Domain use to request this data, completely hiding the fact that we might be using Firebase or a custom backend?

Step 2: Wire Format & Identity.
(AI must explain: Firebase UIDs or specific timestamp types must be mapped to our own internal types).
Question: How are we mapping the remote backend's specific data types into our clean, project-owned Kotlin data classes?

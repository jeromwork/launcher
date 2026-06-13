FRAMEWORK: ARCHITECTURE AND MODULARITY (Article IV & V)

Step 1: Layered Architecture.
(AI must explain: splitting code into UI, Domain, and Data layers).
Question: For this feature, what data needs to be fetched (Data layer), and what business rules apply to it (Domain layer) before showing it (UI layer)?

Step 2: Module Restraint.
(AI must explain: we don't create new Gradle modules unless strictly necessary, per Article V).
Question: Can we build this inside an existing module, or does it require a completely new Gradle module? If new, what is the justification?

Step 3: State Ownership.
(AI must explain: Unidirectional Data Flow (UDF) - UI only reacts to state).
Question: What exact variables will make up the "State" for this screen (e.g., isLoading, userList, errorMessage)?

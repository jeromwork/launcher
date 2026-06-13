FRAMEWORK: DATA AND OFFLINE STRATEGY (Article III)

Step 1: Single Source of Truth.
(AI must explain: UI reads from local database, network updates the database - not UI directly).
Question: Will this feature use a local database (like Room) as the single source of truth, or is it a purely transient network call?

Step 2: Caching Strategy.
(AI must explain: how long we trust local data).
Question: How do we invalidate the cache? Do we refresh data on every app start, or only after a specific time?

Step 3: Optimistic Updates.
(AI must explain: updating UI instantly before the server responds to feel fast).
Question: If the user toggles a switch or likes an item offline, do we update the UI instantly and sync later, or show a loading spinner?

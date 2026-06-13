FRAMEWORK: RELEASE AND BUILD VARIANTS (Article VII)

Step 1: Debug vs Release Config.
(AI must explain: some tools are only for testing).
Question: Does this feature require any developer tools or mock data that we must strictly disable in the production Release build?

Step 2: Minification (ProGuard/R8).
(AI must explain: shrinking code can break reflection or external models).
Question: Are we using any third-party data models here that need special "Keep" rules so they aren't deleted when the app is minified for release?

Step 3: Feature Flags / Rollout.
(AI must explain: releasing gradually is safer).
Question: Should this feature be hidden behind a remote configuration flag initially, so we can turn it off easily if it causes crashes?

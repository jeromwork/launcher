FRAMEWORK: CONFIGURATION AND PROFILES (Article VII)

Step 1: Profile-Driven Behavior.
(AI must explain: features should be configurable via structured JSON/profiles, not hardcoded if/else).
Question: Does this feature behave differently depending on the active user profile? If so, what configuration flags do we need to add to our schema?

Step 2: Schema Versioning & Migration.
(AI must explain: when we change config structure, old profiles shouldn't break).
Question: If we are adding new fields to the configuration, what are the safe default values for existing users who haven't updated?

Step 3: Optional Modules.
(AI must explain: if this feature is a downloadable module, the base app must survive without it).
Question: Is this a core feature or an optional module? If optional, what is the fallback UI if the module is not downloaded?

FRAMEWORK: OBSERVABILITY AND ANALYTICS (Quality Bar & Article XIV)

Step 1: Crash Reporting.
(AI must explain: we need to know if the app crashes for real users).
Question: What critical failure points in this feature should be wrapped in non-fatal crash logging (e.g., Firebase Crashlytics) without exposing personal data?

Step 2: Analytics & Telemetry.
(AI must explain: tracking usage helps us know if the feature is actually used).
Question: What are the 1-2 key user actions on this screen we should track anonymously to measure success?

Step 3: Privacy & Security.
(AI must explain: no PII (Personally Identifiable Information) in logs).
Question: Are we absolutely sure we are not logging any passwords, real names, or exact locations in our analytics for this feature?

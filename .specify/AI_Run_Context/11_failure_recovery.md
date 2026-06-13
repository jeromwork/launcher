FRAMEWORK: FAILURE RECOVERY & EDGE CASES (Article III)

Step 1: API Timeouts & Retries.
(AI must explain: networks are flaky, requests will timeout).
Question: If the API request times out, do we automatically retry, or do we show a "Try Again" button to the user?

Step 2: Corrupted Data / Invalid State.
(AI must explain: what if the backend sends unexpected JSON or the local DB gets corrupted).
Question: How does the UI behave if the data parsing fails? Does it fall back to a safe default?

Step 3: Silent Failures.
(AI must explain: catching exceptions without notifying the user or logging is dangerous).
Question: Where are the potential silent crashes in this flow, and how are we logging them without disrupting the elderly user's experience?

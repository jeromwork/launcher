FRAMEWORK: SYSTEM INTEGRATION & BATTERY (Article VI & IX)

Step 1: Core Module Ownership.
(AI must explain: all system broadcasts and global listeners must live in the 'Core' module).
Question: Does this feature need to listen to system events (like boot, connectivity, or package changes)? If yes, how do we route this through Core?

Step 2: Background Work Justification.
(AI must explain: background tasks drain battery and must be explicitly justified).
Question: Does this feature require any background work (WorkManager/Services)? If yes, can we defer it until the device is charging and on Wi-Fi?

Step 3: Polling vs Event-Driven.
(AI must explain: polling the server constantly is forbidden).
Question: Are we waiting for the server to notify us (e.g., Push/WebSocket), or are we planning to poll? (Hint: Polling requires strong justification).

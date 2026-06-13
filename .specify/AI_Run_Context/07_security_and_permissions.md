FRAMEWORK: SECURITY AND PERMISSIONS (Article XIV)

Step 1: Permission Rationale.
(AI must explain: we only ask for permissions we absolutely need).
Question: Does this feature require any dangerous permissions (Camera, Location, Contacts)? Why can't we build it without them?

Step 2: Graceful Degradation (Denied State).
(AI must explain: the app must not crash if the user clicks "Deny").
Question: What exactly does the user see and do if they permanently deny this permission? How does the core app continue to function?

Step 3: Local-First Privacy.
(AI must explain: avoiding sending sensitive data to the server if it can be processed locally).
Question: Can the data for this feature be kept entirely on the device without ever touching our backend?

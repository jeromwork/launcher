FRAMEWORK: STATE AND LIFECYCLE (Article III & IV)

Step 1: Configuration Changes.
(AI must explain: screen rotations or theme changes recreate the Android Activity).
Question: If the user rotates the screen while this feature is loading, how do we ensure the data isn't requested twice?

Step 2: Process Death.
(AI must explain: Android can kill apps in the background to free memory).
Question: If the user minimizes the app to open the camera, and Android kills our app, what state must we restore when they return?

Step 3: Lifecycle-Aware Collection.
(AI must explain: UI should only listen to data when visible).
Question: Are we safely collecting state only when the screen is active (e.g., repeatOnLifecycle), so we don't waste CPU in the background?

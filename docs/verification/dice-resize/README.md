# Dice resize comparison

Unedited emulator captures from the recent-apps shrinking animation on the
Pixel 9 Pro XL, Android 17 (API 37). System animation scales were temporarily
set to 5x to make intermediate frames easy to inspect, then restored. Frames
were captured independently, so the task sizes and animation times differ.

| Theme | Before: separate GL surface | After: GL texture in the app window |
| --- | --- | --- |
| Light | [Before](light-before.png) | [After](light-after.png) |
| Dark | [Before](dark-before.png) | [After](dark-after.png) |

Open the light before image at full resolution: a thin gray horizontal line
appears below the dice region. The updated view removes that rectangular edge.
These are diagnostic captures, not Play Store artwork.

To reproduce, open the app, enter the recent-apps overview, and watch the area
around the dice while the app shrinks. Repeat in light and dark mode, then
return to the app. Also check Home/restore and Float answer/return after a roll.

The fix uses `TextureView` to compose the existing OpenGL renderer into the
window, with the theme colors and saved rotation supplied before its first
frame. Changing only the old surface's pixel format did not eliminate the seam.
Rendering remains on demand; EGL resources are released while stopped and the
worker is closed when Compose removes the view. TextureView adds window
composition work compared with a separate SurfaceView; this change does not
claim a measured performance improvement.

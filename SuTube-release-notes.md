# Su Tube 0.5.115

Su Tube is a rebuilt and renamed fork of Skipii, made by Suhareb. This release covers everything changed since the fork.

## Branding
- The app is now **Su Tube**, in the launcher, the home header, the update screen and every language.
- New app icon: a violet screen with a white play triangle on near-black, with a matching one-colour version for Android themed icons.
- The app ID is now `com.sutube`, so it installs next to the original Skipii instead of replacing it.
- **About** now says "Made by Suhareb". The Instagram, Threads and X buttons are gone. The email button writes to tyrahbanks8@gmail.com, and there is a new **GitHub** button.
- The navigation tab "Subscriptions" is now **Subs**, so the bar lines up evenly.

## Player
- **Floating miniplayer.** The wide bar is replaced by a small floating video window with play/pause, close and a progress line.
  - Drag it anywhere. It settles on the nearer left or right side and keeps the height you left it at.
  - Flick it down, or drag it below the tab bar, to close it. Tap it or flick it up to open the player.
- **Fullscreen recommendations.** Swipe up in the middle of the video to bring in a row of suggested videos along the bottom. Tap one to play it. Tap outside, swipe down or press Back to close the row. Brightness and volume swipes on the sides work as before.
- **Links in descriptions** are now tappable and open in the right app (browser, YouTube, mail). YouTube's redirect wrappers are removed so the real address opens.
- **Open YouTube links in Su Tube.** Video, Shorts, live and youtu.be links can open in the app. Turn it on under **Settings › About › Open YouTube links here**, which opens Android's "Open by default" page. The "Open in YouTube" escape now goes to the real YouTube app, so it can't loop back into Su Tube.

## Comments
- **Double tap** a comment to like it. **Triple tap** to start a reply (signed in only). Both give a short vibration.
- **Top / Newest** sort buttons beside the comments title, using YouTube's own sort options.
- **Reddit-style thread lines:** a guide line runs from the parent comment's avatar down the replies, with a curved branch into each one.
- **Pinned parent comment:** when a comment's replies are open, that comment stays at the top of the sheet while the replies scroll, so "Hide replies" is always in reach.

- **Replies to replies** get their own thread: when a reply answers an earlier reply (it starts with that person's @name), it sits under that reply with its own "Show replies" dropdown and guide line.
- **Long press** a comment to copy its text. On by default; switch it off under Settings › Playback.
- **Search comments:** a search button in the comments sheet filters the loaded comments by text or author, and keeps reading on while it looks. On by default; switch it off under Settings › Playback.

## Notifications
- A **bell** sits beside the profile picture (and at the top of the Library), with a count of unread notifications. Tap it for your YouTube notifications and tap one to play that video.

## Shorts
- Each short now shows when it was posted, then its view count: minutes, hours, days or weeks ago, and the date itself (dd-mm-yy) once it is a month old.

## Search
- A one-tap filter row under the search bar: **Today / This week / This month / This year**, **Under 4 min / 4–20 min / Over 20 min**, and **Newest / Most viewed**. Tap again to clear. Filters can be combined.
- Filtered searches load more results as you scroll.

## Library
- **History** has a **Show all** button that expands it into your full watch history, loading as you scroll.
  - A search box (title or channel) appears under the History heading.
  - The Library title, the History heading with **Show less**, and the search box stay pinned while you scroll the videos.
- **Watch later** and **Liked videos** are now rows like History. **Show all** opens the usual playlist page, which has filter chips built from what you saved there: video length and your most-saved channels.
- A new greeting under the Library title: your profile picture, "Hello, name", **View channel** on the left and **Settings** on the right. The profile picture no longer sits at the top right of this tab (it is unchanged on the other tabs).

## Home
- New setting, **Dig for unseen videos on refresh** (Settings › Appearance). It is off by default, so Home shows what YouTube picked for you in its own order. Turn it on for the old behaviour of reading further into the list on refresh.

## Appearance
- **Custom font.** Pick a `.ttf` or `.otf` file under Settings › Appearance and it is used across the app. A reset button returns to the phone's font. Emoji keep the phone's emoji unless the chosen font has its own.

## Updates and builds
- The in-app updater now reads releases from `github.com/Suhareb/Su-Tube` and looks for a file named `SuTube.apk` (or the release's only `.apk`).
- Release and debug builds are signed with a fixed key from the repo's secrets, so new versions install over old ones. Without the key the build still works, signed with the debug key.
- Pushing a tag like `v0.5.115` builds the app and publishes a GitHub release with `SuTube.apk` attached, but only when the signing key is available.
- Build downloads under Actions are now called `sutube-apks`.

## Notes
- **First install:** earlier builds were signed with a different key. Uninstall them once before installing this version. Updates will install over it from then on.
- **VPN:** on some VPNs YouTube shows "confirm you're not a robot". This is YouTube blocking the VPN's address and is not changed in this release. Excluding the app from the VPN or switching server helps.
- The new labels (Show all, search filters, sort buttons, font settings) are English-only for now.

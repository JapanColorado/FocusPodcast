How to report a bug
-------------------
- Before anything else, please make sure you are on the latest version, the bug you are experiencing may have been fixed already!
- Use the search function to see if someone else has already submitted the same bug report.
- Try to describe the problem with as much detail as possible.
- Some bugs may only occur on certain devices or versions of Android. Please add information about your device and the version of Android that is running on it (you can look these up under `Settings → About Phone`), as well as which version of FocusPodcast you are using.
- If the bug only seems to occur with a certain podcast, please include the URL of that podcast.
- If possible, add instructions on how to reproduce the bug.
- If possible, add a logfile to your post. This is especially useful if the bug makes the application crash. FocusPodcast has an `report bug` feature for this.
- Usually, you can take a screenshot of your smartphone by pressing *Power* + *Volume down* for a few seconds.
- Please use the following **[template](../../issues/new?labels=Type%3A+Possible+bug&template=bug_report.yml)**.


How to submit a feature request
-------------------------------
- Make sure you are using the latest version of FocusPodcast. Perhaps the feature you are looking for has already been implemented.
- Use the search function to see if someone else has already submitted the same feature request. If there is another request already, please upvote the first post instead of commenting something like "I also want this".
- To make it easier for us to keep track of requests, please only make one feature request per issue.
- Give a brief explanation about the problem that may currently exist and how your requested feature solves this problem.
- Try to be as specific as possible. Please not only explain what the feature does, but also how. If your request is about (or includes) changing or extending the UI, describe what the UI would look like and how the user would interact with it.
- Please use the following **[template](../../issues/new?template=feature_request.yml)**.



Submit a pull request
---------------------
- Before you work on the code
    - Make sure that there is an issue *without* the `Needs: Triage` or `Needs: Decision` label for the feature you want to implement or bug you want to fix.
    - Add a comment to the issue so that other people know that you are working on it.
        - You don't need to ask for permission to work on something, just indicate that you are doing so.
- Fork the repository
- Create a new branch for your contribution
    - This makes opening possible additional pull requests easier.
    - As a base, use the `main` branch.
- Get coding :)
    - If possible, add unit tests for your pull request and make sure that they pass.
    - Upgrade dependencies or build tools only with a concrete reason, in a separate commit, and note why in the commit message. Several pinned versions carry comments explaining why newer releases break.
- Open the PR
    - Mention the corresponding issue in the pull request text, so that it can be closed once your pull request has been merged. If you use [special keywords](https://docs.github.com/en/issues/tracking-your-work-with-issues/linking-a-pull-request-to-an-issue), GitHub will close the issue(s) automatically.


Building From Source
--------------------------
1. Clone the repository. There are no submodules; the vendored libraries live in-tree under `lib/`.
1. Install [pixi](https://pixi.sh) and an Android SDK with platform 35 and build-tools 34.0.0
   (default location `~/Android/Sdk`; set `ANDROID_HOME` to override).
1. `pixi install` downloads JDK 17 into the project environment.
1. `pixi run build` produces the debug APK; `pixi run test`, `pixi run lint` and `pixi run check`
   run the verification tasks. `pixi task list` shows everything available.
1. For release builds copy `secrets.properties.sample` to `secrets.properties` and fill in your
   signing keystore.
1. Android Studio works too: open the project, set Gradle JDK to 17, and use `pixi shell` for the
   terminal so `JAVA_HOME` and `ANDROID_HOME` are set.

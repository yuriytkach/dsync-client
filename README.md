# dsync-client
Dropbox two-way synchronization client.   [![CircleCI](https://circleci.com/gh/yuriytkach/dsync-client.svg?style=svg)](https://circleci.com/gh/yuriytkach/dsync-client)

Get the latest working version here: https://github.com/yuriytkach/dsync-client/releases/latest

You can download the distribution, unzip and use it. Run the \*.sh file and follow instructions on screen.

If you want to compilte from sources, then clone the project. Don't forget to register new app in [Dropbox app console](https://www.dropbox.com/developers/apps) and specify the `APP_KEY` and `APP_SECRET` as environment variables.

Uses the official Dropbox API v2. No passport is required or stored by the app, because oAuth is used.

**Basic features are working now!** Clone the project and do `mvn package` to get the full project in `target`.

There is still room for improvement (like writing good readme :) 

As of now, features that work:
* Continuously sync files/folders from server to local dir
* Continuously sync files/folders from local dir to server
* No offline local changes detection (so if you change localy something when client is not running, it won't pick up changes on start). Therefore, no conflict handling

## Quick links

|Item                  |Link                                                                                      |
|:---------------------|:-----------------------------------------------------------------------------------------|
|Continuous integration| [![CircleCI](https://circleci.com/gh/yuriytkach/dsync-client.svg?style=svg)](https://circleci.com/gh/yuriytkach/dsync-client)                       |
|Dependencies          |[![Dependency Status](https://www.versioneye.com/user/projects/58cc455acef500003fd3bf59/badge.svg?style=flat-square)](https://www.versioneye.com/user/projects/58cc455acef500003fd3bf59) |


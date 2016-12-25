# dsync-client
Dropbox two-way synchronization client

**Basic features are working now!** Clone the project and do `mvn package` - get the full project in `target`.

There is still room for improvement (like writing good readme :) 

As of now, features that work:
* Continuously sync files/folders from server to local dir
* Continuously sync files/folders from local dir to server
* No offline local changes detection (so if you change localy something when client is not running, it won't pick up changes on start). Therefore, no conflict handling

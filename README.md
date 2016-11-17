# dsync-client
Dropbox two-way synchronization client

Under heavy development right now :) I'll post a good description with instructions when I have stable version with minimum amount of features:
* Sync events from server to local dir
* Sync events on local dir to server
* No conflict handling (for now: server wins)
* Minimum optimizations for events handling (e.g. treat delete+create without size change as rename, etc)
* No use of fancy technologies (for now: plain Java, jdbc, nio)

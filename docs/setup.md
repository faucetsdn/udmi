# UDMI tools setup

## Cloud Setup

Ah yes -- the authentication model has changed slightly, so it's now using the standard Google default tools, as
described in the gcloud SDK setup guide, then sign-in with something like `gcloud auth login`. You'll need to be
authenticated with an appropriate account, which in this case likely would be the same account used to access the
cloud project itself (but could be the service account, if necessary). You can check this with 
`gcloud auth list | fgrep \*` (or without the `fgrep` to see all options).



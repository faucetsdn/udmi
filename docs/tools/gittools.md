[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [Git Tools](#)

# UDMI git Utilities

The UDMI git tools are a collection of utilities that have been developed for working
with git branches. They really have nothing to do with UDMI, but rather just
'things I had on my dev machine' that might be useful for the wider community. Effectively
using git will still require some detailed knowledge of how it works to resolve conflicts,
etc...

* git-branch-status: Shows the status of all local branches as configured against their origin or parents.
* git-branch-update: Updates all local branches and upstream repo to keep things in sync.
* git-branch-remote: Scans upstream remote branches and makes suggestions for purging them.

# Tool Setup

## Installing

The tools are located in `bin/git-*` in the UDMI repo. To use them, either copy them to somewhere
in your environment's $PATH, add `udmi/bin` to the $PATH, or invoke them directly. If installed
in the $PATH you should be able to use (e.g.) either `git branch-status` or `git branch-status`
from anywhere, or `bin/git-branchstatus` to access them direct.

## Upstream Configuration

To work with upstream remotes, that is some canonical source that is not your personal repo but
you want to synchronize with:

* `git config upstream.main $main` will set the main branch, `$main` is usually `main` or `master`
* `git config upstream.remote $remote` sets the upstream remote to `$remote` (as listed in `git remote`

## Branch Configuration

Each local branch also works with tracking information to help sort through the needful:

* `git config branch.$branch.description "SOME WORDS HERE"` as a reminder of what the branch is for.
* `git config branch.$branch.parent $main` to set the parent (local) branch that $branch should track.

# Decyphering Tool Output

## git branch-status

## git branch-update

## git branch-remote


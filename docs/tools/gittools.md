[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [Git Tools](#)

# UDMI git Utilities

The UDMI git tools are a collection of utilities that have been developed for working
with git branches. They really have nothing to do with UDMI, but rather just
'things I had on my dev machine' that might be useful for the wider community. Effectively
using git will still require some detailed knowledge of how it works to resolve conflicts,
etc...

* `git-branch-status`: Shows the status of all local branches as configured against their origin or parents.
* `git-branch-create`: Create a new clean branch with provided description.
* `git-branch-update`: Updates all local branches and upstream repo to keep things in sync.
* `git-branch-remote`: Scans upstream remote branches and makes suggestions for purging them.

Generally speaking, a usual workflow consists of running `git status` a lot, `git branch-status`
frequently (if working with multiple branches), `git branch-upate` daily or when getting close to
finalization, `git branch-create` as needed, and then `git branch-remote` rarely just to keep
things sober.

# Tool Setup

## Installing

The tools are located in `bin/git-*` in the UDMI repo. To use them, either copy them to somewhere
in your environment's $PATH, add `udmi/bin` to the $PATH, or invoke them directly. If installed
in the $PATH you should be able to use (e.g.) either `git branch-status` or `git-branch-status`
from anywhere, or `bin/git-branchstatus` to access them direct.

## Upstream Configuration

To work with upstream remotes, that is some canonical source that is not your personal repo but
you want to synchronize with:

* `git config upstream.main $main` will set the main branch, `$main` is usually `main` or `master`
* `git config upstream.remote $remote` sets the upstream remote to `$remote` (as listed in `git remote`)

## Branch Configuration

Each local branch also works with tracking information to help sort through the needful. This is
handled automatically by `git-branch-create`:

* `git config branch.$branch.description "SOME WORDS HERE"` as a reminder of what the branch is for.
* `git config branch.$branch.parent $main` to set the parent (local) branch that $branch should track.

# Decyphering Tool Output

## git branch-status

The `branch-status` utility only works with local information (so it's fast) and gives a view of
the current state of the repo. It does _not_ automatically sync with any designated remote, so can
show out-of-date info if it hsan't been updated in a while (e.g. by running `branch-update`).

* `ahead X`: Branch is X commits ahead of its upstream/parent.
* `behind X`: Branch is X commits behind its upstream/parent.
* `insync`: Branch is neither ahead-nor-behind its upstream/parent.
* `disjoint`: Indicates that the branch has forked from the parent, and is both ahead and behind.

```
~/udmi$ git branch-status 
gittools       ahead 1; ahead master 5       Tools for working with git
mapping        insync; ahead master 41       Mapping agent
master         insync; insync master
registrar      insync; ahead master 85       SiteModel abstraction for registrar
```

## git branch-create

Creates a new branch and configures the relevant properties for other git-branch-* tools. Really
not very exciting but streamlines a few things and hopefully avoids some mistakes.

```
~/udmi$ git branch-create sequences Programmatically create sequences
Already on 'master'
Your branch is up to date with 'origin/master'.
Switched to a new branch 'sequences'
```

## git branch-update

The `branch-update` tool does a bunch of routine things to make everything as up-to-date as possible.
Any merge conflicts or similar issues will need to be manually resolved. Roughly speaking, the tool
does the following operations:
* Syncs with configured upstream remote.
* Syncs local `$main` branch with upstream remote `$main`.
* Syncs merges configured parent branches into local branches.
* Pulls origin changes down (and merges).
* Pushes pending updates to origin.

```
~/udmi$ git branch-update
Fetching upstream remote faucet master branch...
Fetching upstream origin...
Updating local master branch...
Updating branch gittools...
  Push to upstream origin/gittools...
Updating branch mapping...
Updating branch master...
Updating branch registrar...
Returning to local branch gittools.
```

## git branch-remote

The `branch-remote` tool scans the origin remotes and tries to clean some junk out. It will
suggest deleting an origin branch if it's identical to the `$main` branch, or if it
hasn't been merged in quite some time. The output can just be cut-and-pasted into a terminal
window to perform the operations.

To ignore the prune commentaty for upstream remotes (e.g. a coworker's repo), set an appropriate
configuration value `remote.${remote}.upstream=ignore`.

```
:~/udmi$ git branch-remote
Updating/pruning remmote faucet...
Updating/pruning remmote quincy...
Updating/pruning remmote origin...
Checking local remote references...
  git push quincy -d auth_login_line  # Stale, last merged 1 year, 10 months ago
  git push quincy -d dependabot/npm_and_yarn/udms/follow-redirects-1.14.8  # Stale, last merged 6 months ago
  git push quincy -d dependabot/npm_and_yarn/udms/karma-6.3.14  # Stale, last merged 7 months ago
  git push quincy -d renovate/actions-setup-java-2.x  # Stale, last merged 1 year, 1 month ago
  git push quincy -d renovate/angular-monorepo  # Stale, last merged 1 year, 1 month ago
  git push origin -d feature  # Stale, last merged 6 months ago
  git push origin -d fix_schema  # Stale, last merged 3 months ago
  git push origin -d fixes  # Stale, last merged 10 months ago
  git push origin -d logging  # Stale, last merged 5 months ago
  git push origin -d merging  # Stale, last merged 3 months ago
  git push origin -d partial  # Stale, last merged 10 months ago
  git push origin -d provisioner  # Stale, last merged 1 year, 11 months ago
  git push origin -d regfix  # Stale, last merged 1 year, 7 months ago
```

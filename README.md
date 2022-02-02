# re-oidc

[![CI](https://github.com/yetanalytics/re-oidc/actions/workflows/ci.yml/badge.svg)](https://github.com/yetanalytics/re-oidc/actions/workflows/ci.yml)
[![Clojars Version](https://img.shields.io/clojars/v/com.yetanalytics/re-oidc)](https://clojars.org/com.yetanalytics/re-oidc)

A wrapper for [oidc-client-js](https://github.com/IdentityModel/oidc-client-js) providing [OIDC](https://openid.net/specs/openid-connect-core-1_0.html) support for re-frame + reagent applications in cljs. Inspired by [re-frame-oidc](https://github.com/tafarij/re-frame-oidc).

## Overview

Re-frame fx, event handlers and subscriptions are provided to allow interactive sign-in from an SPA without the need for a server backend.

## Configuration

Re-oidc requires a map of configuration options:

| key                    | type     | default   | description                                                          |
| -------------          | -------  | --------- | ----------------------------------------------------------------     |
| `:auto-login`          | boolean  | false     | If no logged-in user is found, automatically initiate OIDC login     |
| `:on-login-success`    | callback | none      | Action to perform after a login redirect is processed                |
| `:on-login-failure`    | callback | none      | Action to perform after a login redirect failure                     |
| `:on-logout-success`   | callback | none      | Action to perform after a logout redirect is processed               |
| `:on-logout-failure`   | callback | none      | Action to perform after a logout redirect failure                    |
| `:on-get-user-success` | callback | none      | Action to perform when an active logged-in user is found on init     |
| `:on-get-user-failure` | callback | none      | Action to perform when an active logged-in user is not found on init |
| `:oidc-config`         | map      | none      | The configuration for oidc-client-js as a clojure map.               |

### Callbacks

Callbacks can be a clojure function or a partial re-frame dispatch vector ex. `[:my-login-success]`. The vector will be dispatched with the result of the callback (possibly an error), which is directly passed from the js OIDC client.

`:on-login-success` and `:on-logout-success` are the two most important callbacks. These should generally apply front-end routing in your application to clear the query string used, see the [demo](src/dev/com/yetanalytics/re_oidc/demo.cljs) for a simple example, where push-state is used to go back to `/` with no query string.

### OIDC Client Config

See [the demo OIDC config](resources/public/oidc.json) for an example of what the `:oidc-config` key expects. Note that `monitorSession` MUST be false. Currently its iframe breaks the figwheel environment, and we have not implemented handling for session events.

## re-frame Usage

### Handlers

Use the following handlers to define the auth lifecycle of your application:

* `[:com.yetanalytics.re-oidc/init <configuration map>]` - Initialize the OIDC client. If called after `:com.yetanalytics.re-oidc/login-callback` or `:com.yetanalytics.re-oidc/logout-callback`, will also process the login/logout. If no callback is present, will attempt to find a logged-in user in storage, calling `:on-get-user-success` if found. Accepts all keys in the configuration map and *requires* the `:oidc-config` key.
* `[:com.yetanalytics.re-oidc/login-callback <configuration map> <query string>]` - Receive data from the OIDC login callback query. If called after :com.yetanalytics.re-oidc/init, will also process login in the client. Accepts the login callbacks in the configuration map and ignores all others.
* `[:com.yetanalytics.re-oidc/logout-callback <configuration map>]` - Register an OIDC logout redirect. If called after :com.yetanalytics.re-oidc/init, will also process logout in the client. Accepts the logout callbacks in the configuration map and ignores all others.
* `[:com.yetanalytics.re-oidc/login]` - Triggers the login redirect, for instance from a user button press. Note that this will not work if fired before initialization, so make its display contingent on the `:com.yetanalytics.re-oidc/status` sub (see below).
* `[:com.yetanalytics.re-oidc/logout]` - Triggers the logout redirect, for instance from a user button press. Note that this will not work if fired before initialization, so make its display contingent on the `:com.yetanalytics.re-oidc/status` sub (see below).

Note that it is useful to define the static parts of your configuration (like callbacks) separately from possibly dynamic values like the OIDC config since not all handlers require the entire config map.

### Subs

Your application should use the following subs to perceive the auth state:

* `[:com.yetanalytics.re-oidc/status]` - Get the status of the OIDC client which can be:
  * `nil` - `:com.yetanalytics.re-oidc/init` has not been called.
  * `:init` - `:com.yetanalytics.re-oidc/init` has been called, no login/logout yet.
  * `:loaded` - A logged-in user is present and can be accessed with the `:com.yetanalytics.re-oidc/user` sub.
  * `:unloaded` - A user was logged out, and is no longer available.
* `[:com.yetanalytics.re-oidc/user]` - Get the logged-in user, if present. The user is a map with the following properties:
  * `:access-token` - OIDC Access Token.
  * `:expires-at` - When, in seconds from the epoch, the access token will expire.
  * `:refresh-token` - OIDC Refresh Token
  * `:id-token` - OIDC Identity Token
  * `:token-type` - Always "Bearer"
  * `:scope` - Granted auth scope of the Access Token
  * `:profile` - Arbitrary claims about the user from the IDP.
* `[:com.yetanalytics.re-oidc/profile]` - Just the OIDC claims about the logged-in user if present.
* `[:com.yetanalytics.re-oidc/logged-in?]` - Boolean that is true if the status is `:loaded`.

### CoFx

* `:com.yetanalytics.re-oidc/user-manager` - Re-frame co-effect to return the OIDC js client for customization.

## Interactive Development & Demo

Before you start, you'll need a keycloak instance. With Docker installed, run:

    make keycloak-demo

To set up a demo Keycloak IDP at localhost:8080.

If you'd like to try sending an access token to a server that will decode it with [pedestal-oidc](https://github.com/yetanalytics/pedestal-oidc) and return it to the frontend, run:

    make api-demo

To launch an interactive development environment run:

    clojure -A:dev:build

or

    make figwheel-dev

Or in EMACS just use `cider-jack-in-cljs` and the included `.dir-locals.el` file will add the correct alias.

When the figwheel browser opens, you'll see a printed out clojure map expressing the state of the DB. Click to log in with the username `dev_user` and password `changeme123`. You should be redirected back to the demo, and you will see login information populate the app db.

If you've also run the API demo server beforehand, you can click "Echo Token" to send it the front end access token. The server will decode it an echo it back, where you can see it stored at the `:com.yetanalytics.re-oidc.demo/token-echo` key in the DB.

## Trying Advanced Compilation

To perform advanced compilation and serve the result:

    make serve-advanced

## Testing

The lib uses browser-based tests. Please ensure that you have `chromium` on your `$PATH`. For instance on a Mac you can install it with:

    brew install chromium

To run the tests, run:

    make test


## License

Copyright © 2022 Yet Analytics, Inc.

Distributed under the Apache License version 2.0.

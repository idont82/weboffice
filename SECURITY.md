# Security

## Reporting a vulnerability

Do not open a public issue. Use GitHub's private vulnerability reporting —
the **Report a vulnerability** button under the repository's Security tab —
or write to idont82@gmail.com.

Please include what the bug lets an attacker do, the steps to reproduce it,
and the version or commit you tested.

This is a one-person project, so there is no response-time guarantee. You
will get an acknowledgement, and a fix lands on `main` once there is one.

## What is in scope

Anything in this repository: the Spring Boot server, the browser bundle, the
schema and the Docker setup.

Two things are worth knowing before you report:

- **Open mode is unauthenticated by design.** With `WO_AUTH_MODE=open`
  (the default) anyone who can reach the server gets a guest identity and can
  create and edit documents. That is the intended behaviour, not a
  vulnerability. Run `registered` mode if you need accounts.
- **The server trusts its own network.** There is no rate limiting and no
  tenant isolation. It is built to sit behind your own reverse proxy and
  authentication, not on the open internet.

A way to read or write *another* user's documents in `registered` mode, to
escalate to admin, or to escape the container is in scope and worth
reporting.

## Dependencies

Dependency versions and their licenses are in
`src/main/resources/oss-manifest.json`. Report a vulnerability in a
dependency to that project first; tell us too, so the version can be raised
here.

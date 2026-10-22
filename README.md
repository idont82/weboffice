# Web Office

A self-hosted web office: open, view and edit Word documents and spreadsheets
in the browser, with real-time collaborative editing on sheets.

Built on [Univer](https://github.com/dream-num/univer) for rendering and
[Apache POI](https://poi.apache.org/) for file handling, with a Spring Boot
backend and PostgreSQL.

> **Status: early.** It runs and it is tested (850 backend + 669 frontend
> tests), but see [What it does not do](#what-it-does-not-do) before you
> rely on it.
>
> `docker compose up` has been run and exercised end to end: both containers
> reach a healthy state, a visitor gets a guest identity, documents and cells
> persist, every page and bundle is served, administration is closed in open
> mode, and the whole administrator invitation flow works in `registered`
> mode. See [What it does not do](#what-it-does-not-do) for the real gaps.

## Run it

```sh
docker compose up
```

Then open <http://localhost:8710>.

That is the whole installation. Compose starts PostgreSQL, loads the schema
into it once, builds the app and waits for the database to accept queries
before starting it.

There is **no login screen** by default. Everyone who opens the page gets a
guest identity stored in a cookie, and can create and share documents.
See [Who can get in](#who-can-get-in).

To start over with an empty database:

```sh
docker compose down -v
```

### Without Docker

You need JDK 21 and a PostgreSQL database. The frontend is built by Maven —
it downloads its own Node.js, so you do not need one installed.

```sh
psql -h <host> -U <user> -d <database> -f db/schema-postgresql.sql

export WO_DB_HOST=<host> WO_DB_NAME=<database> \
       WO_DB_USER=<user> WO_DB_PASSWORD=<password>
mvn package
java -jar target/*.jar --spring.profiles.active=postgresql
```

## What it does

**Documents (.docx)** — open, edit and export. Headers and footers, including
first-page-only ones; page-number fields that recount per page; tables;
floating images; content controls. Page counts are checked against the
originals, not assumed.

**Spreadsheets (.xlsx)** — open, edit and export. 540 formula functions,
conditional formatting, data validation, sorting, filtering, tables, notes,
threaded comments, hyperlinks, images.

**Real-time collaboration on sheets** — cells live as database rows, not in a
file, so edits, row heights, merges, images and the sheet tab bar are relayed
to everyone with the document open over WebSocket. You see where other people
are working. Changes are logged; restore points can be taken and rolled back.

**Korean documents (.hwp, .hwpx)** — import only. Export is .docx.

**An embed API** — another system can `POST` a document and get back a
single-use URL that opens it read-only in a browser. Off by default; see
[configuration](#configuration).

## What it does not do

Known and deliberate. Listed so you can judge before installing, not after.

- **No collaborative editing of text documents.** Only sheets are real-time.
  If two people open the same document, the later save overwrites the
  earlier one's work entirely, because documents are saved as whole
  snapshots.
- **Sheet conflicts are last-write-wins.** This is not OT or CRDT. If two
  people edit the same cell, one edit is silently lost, with no warning.
- **No pivot tables, charts or sparklines.** These are paid-tier features of
  the rendering engine.
- **Tables inside headers are not drawn.** An engine limitation: passing one
  through blanks the whole header.
- **DRM-protected files are not recognized.** Detection lived in a commercial
  library that is not redistributable. Such a file fails as corrupt.
- **No email sending.** Invitations are links you copy and pass along.

## Who can get in

One setting decides.

```yaml
weboffice:
  auth:
    mode: open        # open | registered
```

### `open` (the default)

No login screen. Each visitor gets a guest identity in an `HttpOnly` cookie
and shows up as `손님-ab12`. Guests own the documents they create, appear in
the presence layer and in change history. Clearing the cookie makes you a
different person.

Ownership and sharing are enforced: you cannot open someone else's private
document. But anyone can walk in and make an identity, so **run this mode on
a trusted network only.** Administration screens are closed in this mode —
nobody can hold a role, so nobody can reach the audit console.

### `registered`

Only people an administrator has registered can get in.

1. Set `WO_AUTH_MODE=registered` and `WO_ADMIN_EMAIL=you@example.com`.
2. On first start, the server creates that account and prints a single-use
   invitation link **to its own log**. Copy it from there.
3. Open the link, choose a password, and you are in as an administrator.
4. Register others at `/admin/users`. Each registration returns an invitation
   link; pass it to them however you like.

Administrators never see or set anyone else's password — the invited person
chooses it. Passwords are stored as BCrypt hashes; invitation tokens are
stored only as SHA-256 hashes, so a leak of the table does not hand over
accounts.

## Configuration

Everything has a working default. Set these as environment variables.

| Variable | Default | What it does |
|---|---|---|
| `WO_PORT` | `8710` | Port on the host |
| `WO_DB_HOST` `WO_DB_PORT` `WO_DB_NAME` `WO_DB_USER` `WO_DB_PASSWORD` | compose fills these | Database connection |
| `WO_AUTH_MODE` | `open` | `open` or `registered` |
| `WO_ADMIN_EMAIL` | empty | First administrator, `registered` mode only |
| `WO_INVITE_HOURS` | `72` | How long an invitation link lives |
| `WO_WS_ORIGINS` | localhost | Origins allowed to open a WebSocket. Set this to your own host when you serve from one. Never `*` — any site could then open a connection with a visitor's session cookie. |
| `WO_VIEW_ENABLED` | `false` | The embed API. `POST /api/view/documents` does not use session authentication — it is guarded only by an API key and an address range, so you turn it on and set those yourself in `weboffice.view.callers`. |

## Build and test

```sh
mvn test                                     # backend, 850 tests
cd frontend && npm ci && npx vitest run      # frontend, 669 tests
```

`mvn test` needs no database you installed and no Docker. That is deliberate:
a test suite you cannot run is a test suite nobody runs.

Most of it runs on in-memory H2. A handful of tests start a **real
PostgreSQL** — the `embedded-postgres` dependency ships the binaries and runs
`initdb` in a temp directory as your own user. Those tests cover what H2
cannot: the production schema top to bottom, the partial index, `jsonb`
rejecting malformed JSON, `bytea` round-tripping, and the server booting and
serving on PostgreSQL with the PostgreSQL mapper branch selected. They found
four missing `DROP TABLE` statements the H2 path could not see.

`SchemaParityTest` keeps the two schemas aligned table by table and column by
column. `PublicReleaseScanTest` refuses configuration files that bake in a
password or a real IP address.

## License

AGPL-3.0-or-later. See [`LICENSE`](LICENSE).

What that means in practice: you can run it, change it and self-host it
freely. If you offer a modified version to other people over a network, you
have to offer them its source too.

If that doesn't work for you, a commercial license is possible — the
copyright is held by one person, so there is nobody else to ask. Write to
**idont82@gmail.com**. Bugs and questions belong in
[Issues](https://github.com/idont82/weboffice/issues); use the address for
licensing, and see [`SECURITY.md`](SECURITY.md) for vulnerabilities.

Every dependency and its license is listed in
`src/main/resources/oss-manifest.json`; `OssManifestDriftTest` fails the build
if that file and `pom.xml` disagree. Univer and the rest are Apache-2.0 or
MIT, which AGPL-3.0 can include.

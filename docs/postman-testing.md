# Testing with Postman

Two collections provide the same 14 requests, with response assertions and demo
credentials. They cover all controller endpoints except `/ping`, which is omitted
from the current collections.

| File | Usage |
|---|---|
| [Literal collection](../fortify/vulnapp.postman_collection.json) | No Postman variables or environment required; edit literal request values directly |
| [Environment collection](../fortify/vulnapp-environment.postman_collection.json) | Uses placeholders supplied by the selected Postman environment; no collection variables |
| [Local environment](../fortify/vulnapp-local.postman_environment.json) | Import and select with the environment collection; contains local URLs and demo credentials |

> This application is intentionally insecure. Use an isolated local test instance;
> never deploy it or expose it on a network.

## Start and import

1. From the repository root, build and start the application:

   ```bash
   mvn -B clean verify
   mvn spring-boot:run
   ```

2. Open the Postman desktop app, or use Postman's web app with its Desktop Agent
   so requests can reach localhost.
3. Choose **Import** and select
   [vulnapp.postman_collection.json](../fortify/vulnapp.postman_collection.json).
4. For the literal collection, edit request URLs and inputs directly if needed.
   Alternatively, import the environment collection and local environment above,
   select **Vulnapp - Local** in the environment selector, and edit its `baseUrl`
   value. Both versions default to `http://localhost:8080`, without a trailing slash.
5. Send individual requests, or run the collection in its listed order with the
   Collection Runner. Review the response assertions in the test results.

## Run with Newman

Install a supported Node.js LTS release with npm. Start the application as described
above, then run these commands from the repository root in another terminal. Newman
does not start the application and does not require the Postman app or an account.

Run the full collection without a global Newman installation:

```bash
npx --yes newman@6 run fortify/vulnapp.postman_collection.json --timeout-request 10000
```

The first run downloads Newman from npm. Alternatively, install Newman globally
and use `newman` directly:

```bash
npm install --global newman@6
newman run fortify/vulnapp.postman_collection.json --timeout-request 10000
```

To run the environment collection, supply its separate environment file:

```bash
npx --yes newman@6 run fortify/vulnapp-environment.postman_collection.json -e fortify/vulnapp-local.postman_environment.json --timeout-request 10000
```

Override environment defaults with `--env-var`. For example, if the application
listens on port 8081, update both the client-facing URL and server-side fetch URL:

```bash
npx --yes newman@6 run fortify/vulnapp-environment.postman_collection.json -e fortify/vulnapp-local.postman_environment.json --env-var "baseUrl=http://localhost:8081" --env-var "fetchUrl=http://127.0.0.1:8081/greet?name=Postman" --timeout-request 10000
```

`--env-var` does not change literal values in the original collection. For a targeted
run that avoids the Utilities folder's server-specific prerequisites, select folders:

```bash
npx --yes newman@6 run fortify/vulnapp.postman_collection.json --folder "Authentication" --folder "Users and accounts" --folder "Transfers (mutates balances)" --timeout-request 10000
```

This targeted command does not cover the utility endpoints. File download is skipped
by default. After creating the fixture, remove the file request's pre-request script
in the literal collection, or add `--env-var "fileReady=true"` when running the
environment collection with its environment file.
Expected `401`, `400`, and `500` responses pass when their assertions match; an HTTP
error status alone is not a failed test. Transfers still mutate balances on each run.

To generate console output and a JUnit XML report for a CI publisher:

```bash
npx --yes newman@6 run fortify/vulnapp.postman_collection.json --reporters cli,junit --reporter-junit-export target/newman-results.xml --timeout-request 10000
```

Newman returns a nonzero exit code for failed assertions or execution errors. Preserve
that exit code in CI; do not use `--suppress-exit-code`. The JUnit reporter is built in
and needs no extra reporter package. The report is written under the Maven build
output directory and is removed by `mvn clean`.

Postman now describes [Newman as unmaintained](https://learning.postman.com/docs/collections/using-newman-cli/command-line-integration-with-newman/)
and recommends the Postman CLI for new workflows. This repository supplies a Newman-
compatible v2.1 JSON collection; do not convert it to Postman's v3 format for these
commands. For reproducible CI runs, pin an exact Newman 6.x version approved by your
organization rather than relying on the major-version selector used in these examples.

## Authentication

The collection includes these demo login requests:

| Login | Username | Password |
|---|---|---|
| Seeded user | `alice` | `password` |
| Hard-coded admin | `admin` | `P@ssw0rd!` |

Other seeded credentials are `bob` / `abc123` and `carol` / `12345678`.
These are intentionally insecure sample credentials, not deployment secrets.
The datasource password is unrelated to API login.

`POST /login` returns a plain-text hexadecimal Bearer token. The environment collection
stores the seeded-user token in `loginToken` and sends it on the account request, which
expects **200**. Run the seeded-user login first when executing individual requests.
The literal collection does not store or send the token; its anonymous account request
still expects **401**. To test it manually, copy the login response into the account
request's Bearer Token authorization field and change its expected status to **200**.

`GET /accounts/{username}` is the only endpoint requiring authentication. The token
supplies a servlet `Principal`, but the endpoint intentionally does not check account
ownership: log in as Alice and request Bob's account to demonstrate IDOR. Missing,
invalid, or expired tokens return **401**. Tokens expire after one hour, are stored
in memory on a single app instance, and are lost on restart. No authenticated session
cookie is issued. Weak token randomness is retained intentionally; this is not
production authentication. Other requests remain **No Auth**. An invalid-login request
verifies **401** and `Invalid credentials`.

## Environment variables

These values belong to the separate local environment, not either collection.
Postman placeholders resolve from the active environment; remove any same-named
local/data overrides if you want the supplied environment values to be used.
Password/token fields are marked secret for display, but the exported JSON is not
encrypted. It contains only intentionally public demo credentials; do not commit real
credentials or an exported environment containing a real token.

| Variable | Default | Purpose |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | URL reached by Postman |
| `username`, `password` | `alice`, `password` | Seeded-user login; `username` also drives user lookup |
| `adminUsername`, `adminPassword` | `admin`, `P@ssw0rd!` | Demo admin login |
| `loginToken` | Empty | Captured seeded-user token used for account Bearer authorization |
| `accountUsername` | `alice` | Account path parameter |
| `transferFrom`, `transferTo` | `alice`, `bob` | Transfer accounts |
| `transferAmount` | `1.00` | Amount transferred on each run |
| `fileName` | `sample.txt` | File under the server's configured file directory |
| `fileReady` | `false` | Set to `true` after creating the server-side file fixture |
| `fetchUrl` | `http://127.0.0.1:8080/greet?name=Postman` | URL fetched by the application server |
| `redirectTarget` | `/greet?name=Postman` | Expected redirect destination |

`fetchUrl` is resolved from the application server, not the Postman machine. If the
application listens on a different port, update it separately from `baseUrl`.
The default greeting assertions expect `name=Postman` in the fetch and redirect
targets; update those assertions if changing the sample content.

## Expected results and prerequisites

| Requests | Expected result |
|---|---|
| Seeded-user and admin login | `200` with hexadecimal token |
| Invalid login | `401` with `Invalid credentials` |
| User lookup | `200` with the requested seeded user |
| Account lookup | Environment collection: `200` after login; literal collection: `401` without a token |
| Transfer | `200` with `Transfer complete` |
| Transfer to unknown recipient | `400` with `Unknown recipient` |
| Greeting | `200` containing `Hello Postman` |
| Encryption | `200` with nonempty Base64 ciphertext |
| XML parsing | `200` with `Hello Postman` |
| Fetch local greeting | `200` containing `Hello Postman` |
| Redirect | `302` with the configured target in `Location`; following redirects is disabled on this request |
| File download | Skipped by default; `200` after fixture setup |
| Invalid deserialization stream | `500`, documenting current error handling |

### Transfers

Transfers change the in-memory database. Initial balances are `alice: 1000.00`,
`bob: 500.00`, and `carol: 100.00`. Repeated runs consume the sender's balance;
restart the application to restore seeded data. The default requests use positive
amounts and do not attempt to exploit the intentional negative-amount flaw.

### File download

Create `sample.txt` in the application's server-side `app.files.base-dir`, which
defaults to `/tmp/vulnapp-files`. In the environment version, set environment
variable `fileReady` to the string `true`, then send the file request; change
environment `fileName` if using another fixture. In the literal version, remove the
file request's pre-request script to enable it, and edit its `name` query directly.
The collection does not upload or create this file. A missing file produces `500`
in the current application.

### Deserialization

The supplied request sends a deliberately invalid stream and expects `500`. This
documents current behavior; it is not a recommendation for error handling or a
successful deserialization test.

To test success, change that request's body to **binary** and select a trusted file
written by Java `ObjectOutputStream` containing a simple `String`. Update its test
to expect `200` and the response `Deserialized: java.lang.String`. JSON and Base64
text are not Java serialization streams. Never submit untrusted serialized objects.

## ScanCentral DAST considerations

The literal collection removes variable-substitution dependencies, but that alone
does not guarantee compatibility with a particular DAST importer. `localhost` means
the DAST sensor or container, not your development machine; replace literal request
URLs with the address of an isolated test instance reachable from the sensor.
For structured URLs, keep the raw URL, protocol, host, port, path, and query fields
consistent when editing. Keep the server-side fetch destination separate from the
client-facing address.

Do not assume a DAST importer executes the Postman assertions or honors the file
skip script. Create the fixture or exclude the file request through your scan
configuration. Use the environment version only where the tool supports supplying
and resolving Postman environments. Neither collection establishes authentication.

## Validation scope

Both collections were statically checked for JSON parsing, script syntax, matching
request defaults, and environment resolution. ScanCentral DAST compatibility has not
been verified. Use the Collection Runner or Newman against your isolated instance
to verify runtime behavior and the server-specific prerequisites.
# Testing with Postman

The [Vulnapp collection](../postman/vulnapp.postman_collection.json) contains 15
requests covering all 12 controller endpoints, with response assertions and demo
credentials. No separate Postman environment is required.

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
   [vulnapp.postman_collection.json](../postman/vulnapp.postman_collection.json).
4. Open the collection's **Variables** tab. Set `baseUrl` to the application URL;
   the default is `http://localhost:8080`, without a trailing slash.
5. Send individual requests, or run the collection in its listed order with the
   Collection Runner. Review the response assertions in the test results.

## Run with Newman

Install a supported Node.js LTS release with npm. Start the application as described
above, then run these commands from the repository root in another terminal. Newman
does not start the application and does not require the Postman app or an account.

Run the full collection without a global Newman installation:

```bash
npx --yes newman@6 run postman/vulnapp.postman_collection.json --timeout-request 10000
```

The first run downloads Newman from npm. Alternatively, install Newman globally
and use `newman` directly:

```bash
npm install --global newman@6
newman run postman/vulnapp.postman_collection.json --timeout-request 10000
```

Override collection defaults with `--env-var`. For example, if the application
listens on port 8081, update both the client-facing URL and server-side fetch URL:

```bash
npx --yes newman@6 run postman/vulnapp.postman_collection.json --env-var "baseUrl=http://localhost:8081" --env-var "fetchUrl=http://127.0.0.1:8081/greet?name=Postman" --timeout-request 10000
```

For a targeted run that avoids the Utilities folder's server-specific prerequisites
(including `sh` and `ping` on native Windows), select folders explicitly:

```bash
npx --yes newman@6 run postman/vulnapp.postman_collection.json --folder "Authentication" --folder "Users and accounts" --folder "Transfers (mutates balances)" --timeout-request 10000
```

This targeted command does not cover the utility endpoints. A full run still needs
the ping prerequisites described below. File download is skipped by default; after
creating the server-side fixture, add `--env-var "fileReady=true"` to enable it.
Expected `401`, `400`, and `500` responses pass when their assertions match; an HTTP
error status alone is not a failed test. Transfers still mutate balances on each run.

To generate console output and a JUnit XML report for a CI publisher:

```bash
npx --yes newman@6 run postman/vulnapp.postman_collection.json --reporters cli,junit --reporter-junit-export target/newman-results.xml --timeout-request 10000
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

`POST /login` returns a random hexadecimal string. The seeded-user login request
stores it in the collection variable `loginToken` for inspection, but the application
does not validate that token, issue an authenticated session cookie, or configure
Spring Security. Requests therefore use **No Auth**; there is no Bearer-token setup
step to perform. An invalid-login request verifies the `401` response.

`GET /accounts/{username}` requires a servlet `Principal`, which the standalone app
does not supply. Its collection request expects **401**, including after login.
Adding Basic or Bearer headers alone will not enable account access. If testing a
deployment with an external authentication layer that supplies a principal, configure
that layer's actual authentication on the account request and update its assertion.

## Collection variables

| Variable | Default | Purpose |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | URL reached by Postman |
| `username`, `password` | `alice`, `password` | Seeded-user login; `username` also drives user lookup |
| `adminUsername`, `adminPassword` | `admin`, `P@ssw0rd!` | Demo admin login |
| `loginToken` | Empty | Captured login response; not used for authorization |
| `accountUsername` | `alice` | Account path parameter |
| `transferFrom`, `transferTo` | `alice`, `bob` | Transfer accounts |
| `transferAmount` | `1.00` | Amount transferred on each run |
| `pingHost` | `127.0.0.1` | Host pinged by the application server |
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
| Account lookup | `401` without an externally supplied principal |
| Transfer | `200` with `Transfer complete` |
| Transfer to unknown recipient | `400` with `Unknown recipient` |
| Greeting | `200` containing `Hello Postman` |
| Encryption | `200` with nonempty Base64 ciphertext |
| XML parsing | `200` with `Hello Postman` |
| Fetch local greeting | `200` containing `Hello Postman` |
| Redirect | `302` with the configured target in `Location`; following redirects is disabled on this request |
| Ping | `200` with command output, when server prerequisites are met |
| File download | Skipped by default; `200` after fixture setup |
| Invalid deserialization stream | `500`, documenting current error handling |

### Transfers

Transfers change the in-memory database. Initial balances are `alice: 1000.00`,
`bob: 500.00`, and `carol: 100.00`. Repeated runs consume the sender's balance;
restart the application to restore seeded data. The default requests use positive
amounts and do not attempt to exploit the intentional negative-amount flaw.

### Ping

The server executes `sh -c "ping -c 1 ..."`. It needs `sh`, a compatible `ping`
command, and permission to send ICMP traffic. Native Windows typically returns `500`
because `sh` is absent; containers may lack `ping` or ICMP permissions. This is a
server prerequisite, not a Postman authentication failure.

### File download

Create `sample.txt` in the application's server-side `app.files.base-dir`, which
defaults to `/tmp/vulnapp-files`. Set the collection variable `fileReady` to the
string `true`, then send the file request. Change `fileName` if using another fixture.
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

## Validation scope

The collection was statically checked for JSON parsing, controller endpoint coverage,
script syntax, variable references, and seeded credentials. Live API responses were
not verified during collection creation. Use the Collection Runner or Newman against
your isolated instance to verify runtime behavior and the server-specific prerequisites.
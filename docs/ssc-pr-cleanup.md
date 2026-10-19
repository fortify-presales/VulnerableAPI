# Clean Up SSC Versions for Closed Pull Requests

This pattern uses a dedicated Jenkins job to remove the SSC application version associated with a closed GitHub
pull request. It does not run in the PR build itself: the cleanup pipeline must come from a protected branch and
must not execute Jenkinsfile code from the PR being closed.

The example Jenkinsfile is [Jenkinsfile.ssc-pr-cleanup](../examples/jenkins/Jenkinsfile.ssc-pr-cleanup). It listens for GitHub
`pull_request` events with action `closed`, then maps the PR number to an SSC version such as
`VulnerableAPI-Jenkins:PR-42`.

The GitHub repository is `fortify-presales/VulnerableAPI`, and the Jenkins SSC
application name is `VulnerableAPI-Jenkins`. Keep `SSC_APP_NAME` aligned with the scan jobs.
After deploying the updated cleanup Jenkinsfile, run the job once to apply the
repository filter.

## Important: Match the Existing Version Name

The shared `fortifyCi` step defaults `versionName` to Jenkins' `BRANCH_NAME`; the resulting SSC name is
`<appName>:<versionName>`. Confirm the actual version name from the `Fortify target:` line in a PR build before
using this cleanup job. GitHub Branch Source commonly names PR jobs `PR-<number>`, which matches this example's
`PR-` prefix. If your scans use a different version naming rule, update `SSC_VERSION_PREFIX` or the mapping in
the example so cleanup targets exactly the version created by the scan job.

## Jenkins and Webhook Setup

1. Create a dedicated Pipeline job whose script path is `examples/jenkins/Jenkinsfile.ssc-pr-cleanup` and whose SCM revision
   is the protected `main` branch. Do not run this Jenkinsfile as a multibranch PR job.
2. Install the **Generic Webhook Trigger** plugin. The agent needs Node.js/npm and the `linux && node` label; it
   does not need Docker or the Docker Pipeline plugin.
3. Configure `SSC_URL` in the job or folder environment. Add Jenkins Secret Text credential `ssc-pr-cleanup-token` with
   an SSC token issued for the cleanup identity (see below), plus Username with password credential
   `nexus-credentials` with read access to the npm mirror. The Jenkinsfile pins `@fortify/setup` to major version
   2 and fcli to `v3.28.1`; update `FCLI_VERSION` when intentionally upgrading. Add Secret Text credential
   `ssc-pr-cleanup-webhook-token` for the Generic Webhook Trigger.
4. Configure a GitHub repository webhook for **Pull requests** and the `closed` action. Authenticate the
   webhook before it reaches the Generic Webhook Trigger endpoint: use a trusted relay that verifies GitHub's
   `X-Hub-Signature-256` and forwards the trigger token, or configure an equivalent signature-verifying ingress.
   Keep the Jenkins trigger token out of source control and do not expose an unauthenticated trigger endpoint.
5. Run the job once so Jenkins applies the Pipeline trigger configuration. The trigger extracts `action`, PR
   `number`, base repository full name, and `pull_request.merged`; its regexp filter targets closed PRs for
   `fortify-presales/VulnerableAPI`. The Jenkinsfile also ignores non-closed payloads that reach the
   job and validates the repository and PR number before contacting SSC.

The Generic Webhook Trigger token can cause the job to run with Jenkins `SYSTEM` authority. Treat the endpoint
and token as privileged: restrict access to the signed webhook relay, keep the Jenkinsfile on protected `main`,
and do not grant the cleanup job unrelated credentials.

## Dedicated SSC Cleanup Credential

The cleanup job binds `ssc-pr-cleanup-token` to `SSC_TOKEN` for its SSC login. Scan jobs continue to use
`ssc-ci-token`; do not replace that shared credential with an administrator token.

To run cleanup as an SSC administrator:

1. In SSC, issue a token for the administrator identity that permits the required application-version deletion
   operations. Prefer a dedicated service account with only the necessary permissions when possible.
2. In Jenkins, open **Credentials** for a restricted folder containing the cleanup job and add a **Secret text**
   credential. Set **ID** to `ssc-pr-cleanup-token` and **Secret** to the SSC token. Do not put the token in the
   Jenkinsfile or build parameters.
3. Restrict folder access and job configuration permissions to trusted administrators. Folder credentials are
   available to jobs in that folder and its subfolders, not exclusively to this job. Keep untrusted PR jobs out
   of the folder and keep the cleanup pipeline definition on protected `main`.
4. Deploy the updated Jenkinsfile and run **Build with Parameters** with `PR_ACTION=closed`,
   `BASE_REPO=fortify-presales/VulnerableAPI`, a known `PR_NUMBER`, and `DRY_RUN` checked. Confirm the
   expected SSC version is returned before allowing deletion.

The SSC identity comes from the token used by `fcli ssc session login`, not from the Jenkins user who starts
the build. Jenkins does not need to run the job as a Jenkins administrator for SSC administrator access.

## Deletion Behavior

The example defaults `DRY_RUN` to false, so a matching version is deleted when its PR closes. Before enabling the
webhook, run the job manually with `DRY_RUN` checked to preview the target: it resolves the exact
application/version pair to a numeric SSC version ID and prints that version's JSON without deleting it. The query
requires both the configured application name and expected PR version name; no match is a no-op, and output other
than one numeric ID fails closed. Actual deletion uses that numeric ID.

Set `DRY_RUN` to true for any run that should only inspect the target. Set `MERGED_ONLY` to true if versions
should be removed only for merged PRs; by default the example handles both merged and unmerged closures. Deleting
an SSC application version permanently removes its artifacts and issues. Reopening a PR can cause a later scan to
create its SSC version again.

Do not race deletion against an in-flight ScanCentral scan. Coordinate the cleanup with the PR scan job, for
example by having both jobs acquire the same Lockable Resources lock keyed by application and SSC version, or by
waiting for that PR's build to finish before allowing the cleanup stage to run.

The example assumes `SSC_URL`, the credential IDs, npm mirror, Node agent label, repository name, and `PR-<number>`
version convention match your Jenkins and SSC configuration. `@fortify/setup` must be able to reach the npm mirror
and the configured fcli download source (GitHub by default, or an internal mirror). Update those fixed values in
the protected Jenkinsfile when deploying it.
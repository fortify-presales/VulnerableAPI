# Clean Up SSC Versions for Closed Pull Requests

This pattern uses a dedicated Jenkins job to remove the SSC application version associated with a closed GitHub
pull request. It does not run in the PR build itself: the cleanup pipeline must come from a protected branch and
must not execute Jenkinsfile code from the PR being closed.

The example Jenkinsfile is [Jenkinsfile.ssc-pr-cleanup](../examples/jenkins/Jenkinsfile.ssc-pr-cleanup). It listens for GitHub
`pull_request` events with action `closed`, then maps the PR number to an SSC version such as
`jenkins-fcli-scancentral:PR-42`.

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
3. Configure `SSC_URL` in the job or folder environment. Add Jenkins Secret Text credential `ssc-ci-token` with
   an SSC identity authorized to delete application versions, plus Username with password credential
   `nexus-credentials` with read access to the npm mirror. The Jenkinsfile pins `@fortify/setup` to major version
   2 and fcli to `v3.28.1`; update `FCLI_VERSION` when intentionally upgrading. Add Secret Text credential
   `ssc-pr-cleanup-webhook-token` for the Generic Webhook Trigger.
4. Configure a GitHub repository webhook for **Pull requests** and the `closed` action. Authenticate the
   webhook before it reaches the Generic Webhook Trigger endpoint: use a trusted relay that verifies GitHub's
   `X-Hub-Signature-256` and forwards the trigger token, or configure an equivalent signature-verifying ingress.
   Keep the Jenkins trigger token out of source control and do not expose an unauthenticated trigger endpoint.
5. Run the job once so Jenkins applies the Pipeline trigger configuration. The trigger extracts `action`, PR
   `number`, base repository full name, and `pull_request.merged`; it filters to closed PRs for
   `fortify-presales/jenkins-fcli-scancentral`. The Jenkinsfile repeats these checks before contacting SSC.

The Generic Webhook Trigger token can cause the job to run with Jenkins `SYSTEM` authority. Treat the endpoint
and token as privileged: restrict access to the signed webhook relay, keep the Jenkinsfile on protected `main`,
and do not grant the cleanup job unrelated credentials.

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
# Clean Up SSC Versions for Closed Pull Requests

This pattern uses a dedicated Jenkins job to remove the SSC application version associated with a closed GitHub
pull request. It does not run in the PR build itself: the cleanup pipeline must come from a protected branch and
must not execute Jenkinsfile code from the PR being closed.

The example Jenkinsfile is [Jenkinsfile.ssc-pr-cleanup](Jenkinsfile.ssc-pr-cleanup). It listens for GitHub
`pull_request` events with action `closed`, then maps the PR number to an SSC version such as
`jenkins-fcli-scancentral:PR-42`.

## Important: Match the Existing Version Name

The shared `fortifyCi` step defaults `versionName` to Jenkins' `BRANCH_NAME`; the resulting SSC name is
`<appName>:<versionName>`. Confirm the actual version name from the `Fortify target:` line in a PR build before
using this cleanup job. GitHub Branch Source commonly names PR jobs `PR-<number>`, which matches this example's
`PR-` prefix. If your scans use a different version naming rule, update `SSC_VERSION_PREFIX` or the mapping in
the example so cleanup targets exactly the version created by the scan job.

## Jenkins and Webhook Setup

1. Create a dedicated Pipeline job whose script path is `docs/Jenkinsfile.ssc-pr-cleanup` and whose SCM revision
   is the protected `main` branch. Do not run this Jenkinsfile as a multibranch PR job.
2. Install the **Generic Webhook Trigger** and **Docker Pipeline** plugins. The example uses the Nexus image
   `repo.onfortify.com/docker-hosted/fcli-build:1`; the agent needs the `linux && docker` label and access to a
   Docker daemon.
3. Configure `SSC_URL` in the job or folder environment. Add Jenkins Secret Text credential `ssc-ci-token` with
   an SSC identity authorized to delete application versions, plus `nexus-credentials` with read access to the
   image repository. Add Secret Text credential `ssc-pr-cleanup-webhook-token` for the Generic Webhook Trigger.
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

## Preview and Enable Deletion

The example defaults `DRY_RUN` to true. A dry run logs in, resolves the exact application/version pair to a
numeric SSC version ID, and prints that version's JSON without deleting it. The query requires both the configured
application name and the expected PR version name; no match is a no-op, and output other than one numeric ID fails
closed. The actual delete uses that numeric ID.

After verifying several closed-PR dry runs, deliberately change the `DRY_RUN` default to false to enable
automatic deletion. Set `MERGED_ONLY` to true if versions should be removed only for merged PRs; by default the
example handles both merged and unmerged closures. Deleting an SSC application version permanently removes its
artifacts and issues. Reopening a PR can cause a later scan to create its SSC version again.

Do not race deletion against an in-flight ScanCentral scan. Coordinate the cleanup with the PR scan job, for
example by having both jobs acquire the same Lockable Resources lock keyed by application and SSC version, or by
waiting for that PR's build to finish before allowing the cleanup stage to run.

The example assumes `SSC_URL`, the credential IDs, image address, repository name, and `PR-<number>` version
convention match your Jenkins and SSC configuration. Update those fixed values in the protected Jenkinsfile when
deploying it.
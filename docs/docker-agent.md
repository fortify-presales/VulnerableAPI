# Running the pipeline in a container

[Jenkinsfile.docker](../Jenkinsfile.docker) runs the build and Fortify steps inside a container that already
contains fcli, ScanCentral Client, a JDK and Maven. It replaces the `@fortify/setup` npm bootstrap
(`bootstrap: 'preinstalled'` in `fortifyCi`), so no npm registry or fcli/tool-definition mirrors are needed.
The Maven mirror in [.mvn/settings.xml](../.mvn/settings.xml) is still used.

## Why a custom image

The [`fortifydocker/fcli`](https://hub.docker.com/r/fortifydocker/fcli) images contain no build tools:

| Tag | Contents | Usable here |
|---|---|---|
| `3` | fcli only, no shell | No (no shell, no ScanCentral Client) |
| `3-ubi9` | fcli, UBI9 shell | Only as a base |
| `3-ubi9-sc25.4` | fcli, ScanCentral Client in `/opt/fortify/sc-client` (`SC_CLIENT_HOME` set), shell | Best base |

`scancentral package -bt mvn` needs a JDK and Maven, so [docker/Dockerfile.fcli-build](../docker/Dockerfile.fcli-build)
adds them on top of the `-ubi9-sc` image. In `preinstalled` mode the `ci` action runs with `PREINSTALLED=true` and
uses `SC_CLIENT_HOME`, so nothing is downloaded at scan time.

```bash
docker build -f docker/Dockerfile.fcli-build \
  --build-arg FCLI_IMAGE=repo.onfortify.com/docker-public/fortifydocker/fcli:3-ubi9-sc25.4 \
  --build-arg MAVEN_SHA512=<sha512 of the Maven tarball> \
  -t repo.onfortify.com/docker-hosted/fcli-build:1 .
docker run --rm repo.onfortify.com/docker-hosted/fcli-build:1 sh -c 'fcli --version && scancentral --version && mvn -v'
```

Pin an fcli minor or patch tag (`3.<minor>-ubi9-sc<ver>`) and rebuild periodically for base image fixes.

## Other language toolchains

For Node.js, .NET, or Python projects, [docker/Dockerfile.language](../docker/Dockerfile.language) copies the
verified fcli and ScanCentral Client binaries into an official language SDK image. Select the language base at
build time; the Fortify tools remain preinstalled and work with `bootstrap: 'preinstalled'`.

```powershell
$fortifyImage = 'repo.onfortify.com/docker-public/fortifydocker/fcli:3-ubi9-sc25.4'
$target = 'repo.onfortify.com/docker-hosted/fcli-node:1'
docker build -f docker/Dockerfile.language `
  --build-arg "FCLI_IMAGE=$fortifyImage" `
  --build-arg LANGUAGE_IMAGE=repo.onfortify.com/docker-public/library/node:22-bookworm `
  -t $target .
docker run --rm $target sh -c 'fcli --version && scancentral --version && node --version && npm --version'
```

Change the image argument for other languages:

| Language | `LANGUAGE_IMAGE` | Smoke check |
|---|---|---|
| Node.js | `repo.onfortify.com/docker-public/library/node:22-bookworm` | `node --version && npm --version` |
| .NET | `mcr.microsoft.com/dotnet/sdk:10.0` | `dotnet --info` |
| PHP | `repo.onfortify.com/docker-public/library/php:8.4-cli-bookworm` | `php --version` |
| Python | `repo.onfortify.com/docker-public/library/python:3.13-slim-bookworm` | `python --version && pip --version` |

The Node, PHP, and Python image references use the Docker Hub proxy. For .NET, the build host needs access to Microsoft
Container Registry, or Nexus needs a Docker proxy for `https://mcr.microsoft.com`; then set `LANGUAGE_IMAGE` to
that proxy's path-based URL. Use a language version supported by your application.

The shared step's `packageExtraOpts` defaults to `-bt mvn`, so override it for non-Maven projects. For example:

```groovy
// Node.js or Python source that does not need a build-tool integration
fortifyCi(appName: 'orders-api', bootstrap: 'preinstalled', packageExtraOpts: '-bt none')

// .NET SDK projects, when supported by the selected ScanCentral Client and agent platform
fortifyCi(appName: 'orders-service', bootstrap: 'preinstalled', packageExtraOpts: '-bt msbuild')

// Python dependency collection from requirements.txt
fortifyCi(appName: 'analytics-api', bootstrap: 'preinstalled',
    packageExtraOpts: '-bt none -pyr requirements.txt')

fortifyCi(appName: 'orders-php', bootstrap: 'preinstalled',
  packageExtraOpts: '-bt none -hv 8.4')
```

Build or test Node/PHP/Python projects in a preceding application-owned stage when needed. The Python `-pyr` option
installs and collects dependencies, so configure pip to use an approved package mirror. .NET Framework projects
typically need a Windows build environment; verify `scancentral -h package` and your client release before choosing
the .NET build tool integration. Adapt `Jenkinsfile.docker`'s Maven-specific Build stage for the application's
toolchain; only the Fortify container setup is shared.

## Using Nexus

Hostnames below are examples; the Docker repositories need their own HTTP(S) connector port or a reverse proxy
with subdomain/path routing, and a certificate that every Docker daemon trusts.

| Repository | Type | Purpose |
|---|---|---|
| `docker-hub-proxy` | docker proxy, `https://registry-1.docker.io` (Docker Hub index) | Pull `fortifydocker/fcli` without direct internet access |
| `docker-hosted` | docker hosted | Push `fcli-build` |
| `docker-public` | docker group (hosted first, then proxy) | Single pull URL |

Grant the account in Jenkins credential `nexus-credentials` read access to the group; use a separate account
with push rights for the image build job. `Jenkinsfile.docker` logs in through `registryUrl` and
`registryCredentialsId`. See [nexus-mirroring.md](nexus-mirroring.md) for the Maven mirror.

## Docker on the Jenkins node

`Jenkinsfile.docker` uses the Docker Pipeline plugin's `agent { docker }`, which needs the Docker CLI and a reachable
daemon on the node (label `linux && docker`). If the node is itself a container, choose one:

| Option | Notes |
|---|---|
| Mount the host socket (`-v /var/run/docker.sock:/var/run/docker.sock`) | Simplest. Build containers are siblings on the host daemon, so the workspace must be at the same path on the host (or be a shared volume). The socket is effectively root on the host. |
| Docker-in-Docker sidecar (`docker:dind`, `DOCKER_HOST=tcp://dind:2376` with TLS) | Isolated from the host daemon, but needs a privileged container and a workspace volume shared with the sidecar. |
| No Docker | Use the Kubernetes plugin with a pod template that runs the custom image as a `container`, or run the custom image as the Jenkins agent. Preferred where available. |

Install the Docker Pipeline plugin and the Docker CLI in the agent image for the first two options.

## Container user and state

Jenkins starts the container with the agent's UID/GID, which usually has no passwd entry or writable home. The agent
args set `HOME`, `FCLI_USER_HOME` and the Maven `user.home` to `/tmp`, and clear the image entrypoint so Jenkins can keep
the container running. fcli session and tool state still goes under the job's `fortify-ci` temporary directory and is
deleted afterwards.

## Not yet verified

The Node.js language image was built and smoke-tested locally. The Maven image, Python and .NET variants, and
`Jenkinsfile.docker` have not been run in a Jenkins instance. Verify these on a pilot job: `dnf` can install
`java-17-openjdk-devel` from your UBI9 mirror, `JAVA_HOME` matches the installed path, and the container user can
write the workspace. Confirm .NET build-tool compatibility with the selected ScanCentral Client version and agent
platform.

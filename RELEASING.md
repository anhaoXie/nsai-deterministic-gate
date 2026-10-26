# Releasing

The library (`nsai-gate-spring-boot-starter`) is published to Maven Central as
`io.github.durgadasepalli:nsai-gate-spring-boot-starter`. The demo module is not published.

## One-time setup

Repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `CENTRAL_USERNAME` | Central Portal user token, username part |
| `CENTRAL_PASSWORD` | Central Portal user token, password part |
| `SIGNING_KEY` | ASCII-armored private GPG key (`gpg --armor --export-secret-keys KEY_ID`) |
| `SIGNING_PASSWORD` | Passphrase of that key |

The public key must be on a key server (`gpg --keyserver keyserver.ubuntu.com --send-keys KEY_ID`).

## Each release

1. On a branch, set `version` in `build.gradle` to the release version (for example `1.5.0`), move the
   `Unreleased` entries in `CHANGELOG.md` under that version with today's date, and merge the PR.
2. Tag the merge commit and push the tag:
   ```bash
   git switch main && git pull
   git tag -a v1.5.0 -m "v1.5.0"
   git push origin v1.5.0
   ```
3. The **Publish to Maven Central** workflow builds, tests, signs and uploads the bundle.
4. At https://central.sonatype.com/publishing/deployments, check that validation passed and click **Publish**.
   The artifact appears on Maven Central after a short delay (search can take longer).
5. Create the GitHub release for the tag, then set `version` back to the next `-SNAPSHOT` in a follow-up PR.

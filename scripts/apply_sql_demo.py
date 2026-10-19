#!/usr/bin/env python3
"""Apply the intentionally vulnerable email lookup demo to a fresh demo branch."""

import argparse
from pathlib import Path
import re
import subprocess
import sys


CONTROLLER = "src/main/java/com/example/vulnapp/web/UserController.java"
TESTS = "src/test/java/com/example/vulnapp/VulnerableAPITests.java"

ENDPOINT = '''
    // Intentional: separate SQL injection dataflow for the PR scanning demo.
    @GetMapping("/users/by-email")
    @Operation(summary = "Find users by email - SQL injection",
        description = "Intentionally concatenates the supplied email into SQL without parameterization. "
            + "This separate lookup introduces a new SQL injection finding for the PR scanning demo.")
    public List<Map<String, Object>> findUsersByEmail(
        @Parameter(description = "Email address to look up in the seeded users table.",
            schema = @Schema(defaultValue = "alice@example.com")) @RequestParam String email) {
        String sql = "SELECT id, username, email FROM users WHERE email = '" + email + "'";
        return jdbcTemplate.queryForList(sql);
    }
'''

DEMO_TESTS = '''
    @Test
    void emailLookupReturnsTheMatchingUser() throws Exception {
        mockMvc.perform(get("/users/by-email").param("email", "alice@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].USERNAME").value("alice"))
                .andExpect(jsonPath("$[0].EMAIL").value("alice@example.com"))
                .andExpect(jsonPath("$[0].PASSWORD_HASH").doesNotExist());
        mockMvc.perform(get("/users/by-email").param("email", "unknown@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void emailLookupIntentionallyAllowsSqlInjection() throws Exception {
        mockMvc.perform(get("/users/by-email").param("email", "' OR '1'='1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].USERNAME",
                        org.hamcrest.Matchers.containsInAnyOrder("alice", "bob", "carol")));
    }
'''

GUIDE = '''## New SQL injection PR demo

`GET /users/by-email?email=` intentionally concatenates the email parameter into
SQL in `UserController.findUsersByEmail`. This is a separate dataflow from the
existing `/users` vulnerability. Never expose this application publicly.

With the application running locally:

```bash
curl --get --data-urlencode "email=alice@example.com" http://localhost:8080/users/by-email
curl --get --data-urlencode "email=' OR '1'='1" http://localhost:8080/users/by-email
```

The normal request returns Alice; the injection returns all three seeded users.
Run `mvn -B -Dtest=VulnerableAPITests test` to verify the demo.

For a new-then-removed finding demo:

1. Establish the SSC baseline scan before adding this endpoint. The PR comment
   compares successive scans in the same SSC PR version, not arbitrary Git commits.
2. Apply the demo, commit and push it, then wait for the PR scan to finish.
   Look for a new SQL Injection finding in `findUsersByEmail`. Results depend on
   SSC issue matching, scan policy, and rulepack configuration.
3. Only after that scan, replace the concatenated query with
   `jdbcTemplate.queryForList("SELECT id, username, email FROM users WHERE email = ?", email)`.
   Update `emailLookupIntentionallyAllowsSqlInjection` to expect an empty array
   and rename it to describe the fixed behavior.
4. Commit and push the fix separately, then wait for another scan. The email
   finding should be removed; the original `/users` finding remains.

'''


def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise ValueError(f"{label}: expected one insertion anchor; no files changed")
    return text.replace(old, new, 1)


def prepare(root):
    paths = [CONTROLLER, TESTS, "README.md"]
    originals = {path: (root / path).read_text(encoding="utf-8") for path in paths}
    markers = {
        CONTROLLER: "findUsersByEmail",
        TESTS: "emailLookupIntentionallyAllowsSqlInjection",
        "README.md": "## New SQL injection PR demo\n",
    }
    present = [markers[path] in originals[path] for path in paths]
    if all(present):
        # Do not overwrite an applied demo.
        return {}
    if any(present):
        raise ValueError("Partial demo detected. Resolve existing email-demo changes first; no files changed")

    controller = originals[CONTROLLER]
    if not controller.endswith("}\n"):
        raise ValueError("Unexpected controller ending; no files changed")
    controller = controller[:-2] + ENDPOINT + "}\n"

    tests = originals[TESTS]
    count_pattern = r'(jsonPath\("\$\.paths\.length\(\)"\)\.value\()(\d+)(\))'
    if len(re.findall(count_pattern, tests)) != 1:
        raise ValueError("Expected one OpenAPI path-count assertion; no files changed")
    tests = re.sub(count_pattern, lambda m: m[1] + str(int(m[2]) + 1) + m[3], tests)
    anchor = '            .andExpect(jsonPath("$.paths[\'/users\'].get").exists())'
    tests = replace_once(
        tests, anchor,
        anchor + '\n            .andExpect(jsonPath("$.paths[\'/users/by-email\'].get").exists())',
        "OpenAPI endpoint assertion",
    )
    anchor = "    @Test\n    void sampleInitializationPreservesExistingFiles"
    tests = replace_once(tests, anchor, DEMO_TESTS + "\n" + anchor, "Demo tests")

    readme = replace_once(
        originals["README.md"], "## Build locally\n",
        GUIDE + "## Build locally\n", "README guide",
    )
    anchor = "| Vulnerability | Location |\n|---|---|\n"
    readme = replace_once(
        readme, anchor,
        anchor + "| SQL Injection (new PR demo dataflow) | "
        "`web/UserController.java` (`GET /users/by-email?email=`) |\n",
        "Vulnerability table",
    )
    return {CONTROLLER: controller, TESTS: tests, "README.md": readme}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo", type=Path, default=Path(__file__).resolve().parent.parent,
        help="Repository directory (default: repository containing this script)",
    )
    parser.add_argument("--check", action="store_true", help="Validate applicability without changing files")
    args = parser.parse_args()
    root = args.repo.resolve()
    try:
        branch = subprocess.run(
            ["git", "-C", str(root), "symbolic-ref", "--short", "HEAD"],
            check=True, capture_output=True, text=True,
        ).stdout.strip()
        if branch in {"main", "master"}:
            raise ValueError("Create a demo branch first; refusing to modify main/master")
        changes = prepare(root)
        if not changes:
            print("Demo already present; no files changed.")
            return
        if args.check:
            print(f"Demo can be applied on {branch}; no files changed.")
            return
        for path, text in changes.items():
            (root / path).write_text(text, encoding="utf-8")
        print(f"Applied intentionally vulnerable email demo on {branch}.")
        print("No commit, push, or branch deletion performed.")
        print("Verify: mvn -B -Dtest=VulnerableAPITests test")
        print("Wait for the vulnerable PR scan before applying the parameterized fix.")
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"Error: {error}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()

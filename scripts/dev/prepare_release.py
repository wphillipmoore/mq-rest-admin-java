#!/usr/bin/env python3
"""Automate release preparation: branch, push, PR, auto-merge."""

from __future__ import annotations

import re
import shutil
import subprocess
from pathlib import Path


def read_command_output(command: tuple[str, ...]) -> str:
    """Run a command and return its stdout."""
    result = subprocess.run(command, check=True, text=True, capture_output=True)  # noqa: S603
    return result.stdout.strip()


def run_command(command: tuple[str, ...]) -> None:
    """Run a command and raise on failure."""
    subprocess.run(command, check=True)  # noqa: S603


def ensure_project_root() -> None:
    """Fail fast if invoked outside the repository root."""
    if not Path("pom.xml").is_file():
        message = "Run from the repository root (pom.xml missing)."
        raise SystemExit(message)


def read_version() -> str:
    """Read the version string from pom.xml."""
    text = Path("pom.xml").read_text(encoding="utf-8")
    match = re.search(
        r"<artifactId>mq-rest-admin</artifactId>\s*<version>([^<]+)</version>",
        text,
    )
    if not match:
        message = "Could not find version in pom.xml."
        raise SystemExit(message)
    return match.group(1)


def ensure_on_develop() -> None:
    """Fail if not on the develop branch."""
    branch = read_command_output(("git", "rev-parse", "--abbrev-ref", "HEAD"))
    if branch != "develop":
        message = f"Must be on develop branch (currently on '{branch}')."
        raise SystemExit(message)


def ensure_clean_tree() -> None:
    """Fail if the working tree has uncommitted changes."""
    status = read_command_output(("git", "status", "--porcelain"))
    if status:
        message = "Working tree is not clean. Commit or stash changes first."
        raise SystemExit(message)


def ensure_tool_available(name: str) -> None:
    """Fail if a required tool is not on PATH."""
    if not shutil.which(name):
        message = f"Required tool '{name}' not found on PATH."
        raise SystemExit(message)


def branch_exists(name: str) -> bool:
    """Return True if a branch exists locally or on origin."""
    for ref in (name, f"origin/{name}"):
        result = subprocess.run(  # noqa: S603
            ("git", "rev-parse", "--verify", "--quiet", ref),
            check=False,
        )
        if result.returncode == 0:
            return True
    return False


def create_release_branch(branch: str) -> None:
    """Create a release branch from the current develop HEAD."""
    if branch_exists(branch):
        message = f"Release branch '{branch}' already exists."
        raise SystemExit(message)
    print(f"Creating branch: {branch} (from develop)")
    run_command(("git", "checkout", "-b", branch))


def push_branch(branch: str) -> None:
    """Push the release branch to origin."""
    print(f"Pushing branch: {branch}")
    run_command(("git", "push", "-u", "origin", branch))


def create_pr(version: str) -> str:
    """Create a PR to main and return the PR URL."""
    print("Creating pull request to main...")
    title = f"release: {version}"
    body = (
        "## Summary\n"
        f"- Release {version} to Maven Central\n"
        "\n"
        "Generated with `prepare_release.py`\n"
    )
    result = subprocess.run(  # noqa: S603
        (
            "gh",
            "pr",
            "create",
            "--base",
            "main",
            "--title",
            title,
            "--body",
            body,
        ),
        check=True,
        text=True,
        capture_output=True,
    )
    url = result.stdout.strip()
    print(f"PR created: {url}")
    return url


def enable_auto_merge(url: str) -> None:
    """Enable auto-merge on the PR."""
    print("Enabling auto-merge...")
    run_command(("gh", "pr", "merge", url, "--auto", "--merge", "--delete-branch"))


def main() -> int:
    ensure_project_root()
    ensure_on_develop()
    ensure_clean_tree()
    ensure_tool_available("gh")

    version = read_version()
    branch = f"release/{version}"

    print(f"Preparing release {version}")

    create_release_branch(branch)
    push_branch(branch)
    url = create_pr(version)
    enable_auto_merge(url)

    run_command(("git", "checkout", "develop"))

    print(f"Release {version} preparation complete.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

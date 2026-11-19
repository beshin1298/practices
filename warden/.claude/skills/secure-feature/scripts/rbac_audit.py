#!/usr/bin/env python3
"""Two-layer RBAC audit for Warden.

Compares what the code enforces with what docs/rbac-matrix.md says, for every endpoint:

  layer 1  URL rules in SecurityConfig  (.requestMatchers(...).permitAll() / .authenticated() /
           .hasAuthority("P") / .hasAnyAuthority("P1", "P2"))
  layer 2  the access annotation on the controller method or class
           (@PublicEndpoint / @Authenticated, or @PreAuthorize("hasAuthority('P')") / hasAnyAuthority(...))

Access levels: PUBLIC, AUTHENTICATED, or a set of permission codes (any one is enough).

Findings
  OK               both layers match the spec
  MISMATCH         annotation says one level, the spec another
  UNGUARDED        no access annotation on the method or its class
  AMBIGUOUS        more than one access annotation at the same level
  CUSTOM           @PreAuthorize expression this script does not understand
  URL_LAYER        URL rule differs from the spec (both layers must say the same thing)
  UNKNOWN_PERMISSION  a permission code used in the matrix or the code is not declared in Permissions.java
  UNDOCUMENTED     endpoint exists in code but not in the matrix
  NOT_IMPLEMENTED  matrix row with no endpoint in code

Usage
  python3 .claude/skills/secure-feature/scripts/rbac_audit.py [--root REPO] [--json]

Exit code: 0 = everything OK, 1 = at least one finding, 2 = could not read the inputs.
Standard library only, so it runs anywhere Python 3.9+ is installed.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import asdict, dataclass, field
from pathlib import Path

META_ANNOTATIONS = {"PublicEndpoint": "PUBLIC", "Authenticated": "AUTHENTICATED"}
AUTHORITY_EXPR_RE = re.compile(r"^has(?:Any)?Authority\((.*)\)$")
QUOTED_RE = re.compile(r"""['"]([A-Za-z0-9_]+)['"]""")


def canonical(level: str | None) -> str | None:
    """'USER_READ_ALL, USER_READ_DEPARTMENT' in any order -> one sorted spelling; PUBLIC/AUTHENTICATED unchanged."""
    if level is None or level in ("PUBLIC", "AUTHENTICATED") or level.startswith("CUSTOM:"):
        return level
    return ", ".join(sorted({p.strip() for p in level.split(",") if p.strip()}))


def permissions_in(level: str | None) -> set[str]:
    if level is None or level in ("PUBLIC", "AUTHENTICATED") or level.startswith("CUSTOM:"):
        return set()
    return {p.strip() for p in level.split(",") if p.strip()}
MAPPINGS = {"GetMapping": "GET", "PostMapping": "POST", "PutMapping": "PUT", "PatchMapping": "PATCH", "DeleteMapping": "DELETE"}

# @Name or @Name(...) where the arguments may contain strings with parentheses: @PreAuthorize("hasRole('ADMIN')")
ANNOTATION_RE = re.compile(r'@([A-Za-z_][\w.]*)(\((?:[^()"]|"(?:[^"\\]|\\.)*"|\((?:[^()"]|"(?:[^"\\]|\\.)*")*\))*\))?')
STRING_RE = re.compile(r'"((?:[^"\\]|\\.)*)"')


@dataclass
class Endpoint:
    method: str
    path: str
    access: str | None
    source: str
    problem: str | None = None


@dataclass
class Row:
    method: str
    path: str
    spec: str | None
    code: str | None
    url_layer: str | None
    status: str
    source: str = ""
    notes: list[str] = field(default_factory=list)


# ---------------------------------------------------------------- Java parsing

def strip_comments(src: str) -> str:
    """Removes // and /* */ comments but keeps string literals (URLs contain //). Keeps newlines for line numbers."""
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        if c == '"':
            j = i + 1
            while j < n and src[j] != '"':
                j += 2 if src[j] == "\\" else 1
            out.append(src[i:j + 1])
            i = j + 1
        elif src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j == -1 else j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            block = src[i:(n if j == -1 else j + 2)]
            out.append("\n" * block.count("\n"))
            i = n if j == -1 else j + 2
        else:
            out.append(c)
            i += 1
    return "".join(out)


def annotation_blocks(text: str):
    """Yields lists of consecutive annotations [(name, args, start, end)] separated only by whitespace."""
    block, last_end = [], None
    for m in ANNOTATION_RE.finditer(text):
        if block and text[last_end:m.start()].strip():
            yield block
            block = []
        block.append((m.group(1).split(".")[-1], m.group(2) or "", m.start(), m.end()))
        last_end = m.end()
    if block:
        yield block


def first_path(args: str) -> str:
    """'("/x")' or '(value = "/x")' or '(path = {"/x"})' -> '/x'; no args -> ''."""
    if not args:
        return ""
    named = re.search(r'\b(?:value|path)\s*=\s*\{?\s*"((?:[^"\\]|\\.)*)"', args)
    if named:
        return named.group(1)
    positional = STRING_RE.search(args)
    return positional.group(1) if positional and not re.match(r'\(\s*\w+\s*=', args) else ""


def access_of(block) -> tuple[str | None, str | None]:
    """Returns (level, problem) for the access annotations found in one block."""
    found = []
    for name, args, *_ in block:
        if name in META_ANNOTATIONS:
            found.append(META_ANNOTATIONS[name])
        elif name == "PreAuthorize":
            expr = STRING_RE.search(args)
            expr_text = (expr.group(1) if expr else args).strip()
            compact = expr_text.replace(" ", "")
            authority = AUTHORITY_EXPR_RE.match(compact)
            if compact == "permitAll()":
                found.append("PUBLIC")
            elif compact == "isAuthenticated()":
                found.append("AUTHENTICATED")
            elif authority:
                found.append(canonical(", ".join(QUOTED_RE.findall(authority.group(1)))))
            else:
                found.append(f"CUSTOM:{expr_text}")
    if not found:
        return None, None
    if len(found) > 1:
        return found[0], "AMBIGUOUS"
    if found[0].startswith("CUSTOM:"):
        return found[0], "CUSTOM"
    return found[0], None


def join_paths(base: str, sub: str) -> str:
    path = "/" + "/".join(p for p in (base + "/" + sub).split("/") if p)
    return path


def parse_controller(path: Path, repo: Path) -> list[Endpoint]:
    src = strip_comments(path.read_text(encoding="utf-8"))
    if "@RestController" not in src and "@Controller" not in src:
        return []
    class_match = re.search(r"\bclass\s+\w+", src)
    if not class_match:
        return []

    head, body = src[:class_match.start()], src[class_match.end():]
    class_block = []
    for block in annotation_blocks(head):
        class_block = block  # the last block before "class" belongs to the class
    class_path = next((first_path(a) for n, a, *_ in class_block if n == "RequestMapping"), "")
    class_access, class_problem = access_of(class_block)

    endpoints = []
    for block in annotation_blocks(body):
        mapping = next(((n, a) for n, a, *_ in block if n in MAPPINGS), None)
        if mapping is None:
            continue
        name, args = mapping
        method_access, method_problem = access_of(block)
        access, problem = (method_access, method_problem) if method_access else (class_access, class_problem)
        line = body[:block[0][2]].count("\n") + src[:class_match.end()].count("\n") + 1
        endpoints.append(Endpoint(
            method=MAPPINGS[name],
            path=join_paths(class_path, first_path(args)),
            access=access,
            source=f"{path.relative_to(repo)}:{line}",
            problem=problem or (None if access else "UNGUARDED"),
        ))
    return endpoints


# ---------------------------------------------------------------- URL layer (SecurityConfig)

RULE_RE = re.compile(
    r"\.(requestMatchers\(([^)]*)\)|anyRequest\(\))\s*\.\s*(permitAll|authenticated|hasRole|hasAnyRole|hasAuthority|hasAnyAuthority|denyAll)\(([^)]*)\)")


def ant_to_regex(pattern: str) -> re.Pattern:
    out = re.escape(pattern).replace(r"/\*\*", "(/.*)?").replace(r"\*\*", ".*").replace(r"\*", "[^/]*")
    return re.compile("^" + out + "$")


def parse_url_rules(repo: Path):
    rules = []
    for cfg in (repo / "backend" / "src" / "main" / "java").rglob("*.java"):
        src = strip_comments(cfg.read_text(encoding="utf-8"))
        if "authorizeHttpRequests" not in src:
            continue
        for m in RULE_RE.finditer(src):
            matcher_args, verb, verb_args = m.group(2) or "", m.group(3), m.group(4)
            http_method = re.search(r"HttpMethod\.(\w+)", matcher_args)
            patterns = STRING_RE.findall(matcher_args) if m.group(2) is not None else ["/**"]
            if verb == "permitAll":
                level = "PUBLIC"
            elif verb == "authenticated":
                level = "AUTHENTICATED"
            elif verb == "denyAll":
                level = "DENY"
            elif verb in ("hasAuthority", "hasAnyAuthority"):
                level = canonical(", ".join(STRING_RE.findall(verb_args)))
            else:  # hasRole / hasAnyRole: roles are data now, the matrix speaks in permissions
                level = "ROLE:" + ",".join(STRING_RE.findall(verb_args))
            for p in patterns:
                rules.append((http_method.group(1) if http_method else None, ant_to_regex(p), level))
    return rules


def url_level(rules, method: str, path: str) -> str | None:
    concrete = re.sub(r"\{[^}]+\}", "1", path)
    for http_method, regex, level in rules:  # Spring uses the first matching rule
        if (http_method is None or http_method == method) and regex.match(concrete):
            return level
    return None


# ---------------------------------------------------------------- spec (docs/rbac-matrix.md)

def parse_matrix(matrix: Path) -> list[tuple[str, str, str]]:
    rows, in_table = [], False
    for line in matrix.read_text(encoding="utf-8").splitlines():
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if re.match(r"^\|\s*Method\s*\|\s*Path\s*\|\s*Access\s*\|", line.strip(), re.I):
            in_table = True
            continue
        if not in_table:
            continue
        if not line.strip().startswith("|"):
            break
        if cells[0].startswith("---"):
            continue
        rows.append((cells[0].upper(), cells[1].strip("`"), canonical(cells[2].upper())))
    return rows


def declared_permissions(repo: Path) -> set[str]:
    """Constants of com.warden.role.constant.Permissions (empty set if the file is missing)."""
    files = list((repo / "backend" / "src" / "main" / "java").rglob("Permissions.java"))
    if not files:
        return set()
    return set(re.findall(r'static\s+final\s+String\s+\w+\s*=\s*"([A-Z0-9_]+)"', files[0].read_text(encoding="utf-8")))


def key(method: str, path: str) -> tuple[str, str]:
    return method, re.sub(r"\{[^}]+\}", "{}", path.rstrip("/") or "/")


# ---------------------------------------------------------------- audit

def audit(repo: Path) -> list[Row]:
    matrix = repo / "docs" / "rbac-matrix.md"
    spec_rows = parse_matrix(matrix)
    if not spec_rows:
        raise ValueError(f"No API table (| Method | Path | Access |) found in {matrix}")

    endpoints = []
    for java in sorted((repo / "backend" / "src" / "main" / "java").rglob("*Controller.java")):
        endpoints.extend(parse_controller(java, repo))
    rules = parse_url_rules(repo)

    declared = declared_permissions(repo)
    spec = {key(m, p): (m, p, a) for m, p, a in spec_rows}
    rows: list[Row] = []
    seen = set()
    for ep in endpoints:
        k = key(ep.method, ep.path)
        seen.add(k)
        spec_access = spec.get(k, (None, None, None))[2]
        url = url_level(rules, ep.method, ep.path)
        row = Row(ep.method, ep.path, spec_access, ep.access, url, "OK", ep.source)
        unknown = sorted((permissions_in(spec_access) | permissions_in(ep.access) | permissions_in(url)) - declared)

        if ep.problem:
            row.status = ep.problem
        elif spec_access is None:
            row.status = "UNDOCUMENTED"
            row.notes.append("add a row to docs/rbac-matrix.md (and the Requirements tab)")
        elif declared and unknown:
            row.status = "UNKNOWN_PERMISSION"
            row.notes.append(f"not declared in Permissions.java: {', '.join(unknown)}")
        elif ep.access != spec_access:
            row.status = "MISMATCH"
            row.notes.append(f"annotation says {ep.access}, matrix says {spec_access}")
        elif url != spec_access:
            row.status = "URL_LAYER"
            if spec_access == "PUBLIC":
                row.notes.append("PUBLIC endpoint is not permitAll() in SecurityConfig: anonymous calls get 401")
            else:
                row.notes.append(f"SecurityConfig URL rule says {url}, matrix says {spec_access}")
        rows.append(row)

    for k, (m, p, a) in spec.items():
        if k not in seen:
            rows.append(Row(m, p, a, None, url_level(rules, m, p), "NOT_IMPLEMENTED",
                            notes=["matrix row without a controller method"]))

    order = {"GET": 0, "POST": 1, "PUT": 2, "PATCH": 3, "DELETE": 4}
    rows.sort(key=lambda r: (r.path, order.get(r.method, 9)))
    return rows


def render_markdown(rows: list[Row]) -> str:
    failures = [r for r in rows if r.status != "OK"]
    lines = [
        "| | Method | Path | Matrix | Annotation | URL rule | Status | Source / note |",
        "| --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for r in rows:
        mark = "ok" if r.status == "OK" else "FAIL"
        note = "; ".join(r.notes) if r.notes else r.source
        lines.append(f"| {mark} | {r.method} | `{r.path}` | {r.spec or '-'} | {r.code or '-'} | {r.url_layer or '-'} | {r.status} | {note} |")
    summary = (f"\nRBAC audit: {len(rows)} endpoints, {len(rows) - len(failures)} OK, {len(failures)} finding(s)."
               + ("" if failures else " Code and docs/rbac-matrix.md agree on both layers."))
    return "\n".join(lines) + "\n" + summary


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    default_root = Path(__file__).resolve().parents[4]
    parser.add_argument("--root", type=Path, default=default_root, help="repository root (default: %(default)s)")
    parser.add_argument("--json", action="store_true", help="machine-readable output")
    args = parser.parse_args()

    try:
        rows = audit(args.root.resolve())
    except (OSError, ValueError) as exc:
        print(f"rbac_audit: {exc}", file=sys.stderr)
        return 2

    if args.json:
        print(json.dumps([asdict(r) for r in rows], ensure_ascii=False, indent=2))
    else:
        print(render_markdown(rows))
    return 0 if all(r.status == "OK" for r in rows) else 1


if __name__ == "__main__":
    sys.exit(main())

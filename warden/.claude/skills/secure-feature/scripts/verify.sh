#!/usr/bin/env bash
# Every check the secure-feature skill requires before a change is called "done".
#
#   bash .claude/skills/secure-feature/scripts/verify.sh            # audit + layering + backend tests + frontend build
#   bash .claude/skills/secure-feature/scripts/verify.sh --fast     # audit + layering + frontend typecheck (no Docker needed)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
FAST="${1:-}"

step() { printf '\n\033[1;34m==> %s\033[0m\n' "$1"; }
fail() { printf '\n\033[1;31mFAILED: %s\033[0m\n' "$1"; exit 1; }

step "1/4 RBAC audit: annotations + URL rules vs docs/rbac-matrix.md"
python3 "$ROOT/.claude/skills/secure-feature/scripts/rbac_audit.py" --root "$ROOT" || fail "RBAC audit"

step "2/4 Layering: controller -> service interface -> repository"
JAVA="$ROOT/backend/src/main/java"
web_classes=$(find "$JAVA" -name '*Controller.java' -o -name '*Filter.java')
# Controllers and filters talk to service interfaces only: no repository, mapper, EntityManager or *ServiceImpl.
offenders=$(grep -lE '\b[A-Z][A-Za-z]*(Repository|Mapper|ServiceImpl)\b|EntityManager' $web_classes || true)
[[ -z "$offenders" ]] || { printf '  %s\n' $offenders; fail "layering (call a service interface instead)"; }
# Every @Service class lives in service/impl and implements the interface of the same name.
for impl in $(grep -rlE '^@Service' "$JAVA"); do
  name=$(basename "$impl" .java)
  [[ "$impl" == */service/impl/*Impl.java || "$impl" == */jwt/impl/*Impl.java ]] \
    || fail "layering: $impl is a @Service outside service/impl (split it into interface + Impl)"
  grep -qE "class $name implements ${name%Impl}\b" "$impl" || fail "layering: $name must implement ${name%Impl}"
done
echo "  OK: $(echo "$web_classes" | wc -l) controllers/filters use service interfaces; $(grep -rlE '^@Service' "$JAVA" | wc -l) services are interface + Impl"

if [[ "$FAST" == "--fast" ]]; then
  step "3/4 Backend tests: skipped (--fast)"
else
  step "3/4 Backend integration tests (Testcontainers, needs Docker)"
  (cd "$ROOT/backend" && ./mvnw -q test) || fail "backend tests"
fi

if [[ "$FAST" == "--fast" ]]; then
  step "4/4 Frontend typecheck"
  (cd "$ROOT/frontend" && npm run -s typecheck) || fail "frontend typecheck"
else
  step "4/4 Frontend typecheck + production build"
  (cd "$ROOT/frontend" && npm run -s build) || fail "frontend build"
fi

printf '\n\033[1;32mAll checks passed.\033[0m\n'

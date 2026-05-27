#!/bin/bash
set -e

FEATURE_REQUEST="$1"

if [ -z "$FEATURE_REQUEST" ]; then
    echo "Usage: ./run-feature.sh 'description of the feature'"
    exit 1
fi

# Setup workspace
mkdir -p .claude/workspace/specs
mkdir -p .claude/workspace/architecture
mkdir -p .claude/workspace/implementation

echo "========================================="
echo "Stage 1: Product Manager — writing spec"
echo "========================================="

claude -p "$(cat .claude/agents/pm.md)

---

## Feature Request:
$FEATURE_REQUEST" \
  --allowedTools "Read,Write,Glob,Grep" \
  --output-format json > .claude/workspace/pm-output.json

echo "PRD written to .claude/workspace/specs/PRD.md"
echo ""

echo "========================================="
echo "Stage 2: Architect — designing solution"
echo "========================================="

claude -p "$(cat .claude/agents/architect.md)

---

## Feature Request (for context):
$FEATURE_REQUEST" \
  --allowedTools "Read,Write,Glob,Grep,Bash" \
  --output-format json > .claude/workspace/architect-output.json

echo "Architecture written to .claude/workspace/architecture/"
echo ""

echo "========================================="
echo "Stage 3: Implementation (parallel)"
echo "========================================="

# Run API Manager, Frontend Dev, Backend Dev in parallel
claude -p "$(cat .claude/agents/api-manager.md)" \
  --allowedTools "Read,Write,Glob,Grep,Bash" \
  --output-format json > .claude/workspace/api-manager-output.json &
PID_API=$!

claude -p "$(cat .claude/agents/frontend-dev.md)" \
  --allowedTools "Read,Write,Glob,Grep,Bash" \
  --output-format json > .claude/workspace/frontend-output.json &
PID_FE=$!

claude -p "$(cat .claude/agents/backend-dev.md)" \
  --allowedTools "Read,Write,Glob,Grep,Bash" \
  --output-format json > .claude/workspace/backend-output.json &
PID_BE=$!

# Wait for all three to finish
echo "Running api-manager (PID $PID_API), frontend-dev (PID $PID_FE), backend-dev (PID $PID_BE) in parallel..."

wait $PID_API
echo "  ✓ API Manager done"
wait $PID_FE
echo "  ✓ Frontend Dev done"
wait $PID_BE
echo "  ✓ Backend Dev done"
echo ""

echo "========================================="
echo "Stage 4: Platform Engineer"
echo "========================================="

claude -p "$(cat .claude/agents/platform-eng.md)" \
  --allowedTools "Read,Write,Glob,Grep,Bash" \
  --output-format json > .claude/workspace/platform-output.json

echo "  ✓ Platform Engineer done"
echo ""

echo "========================================="
echo "Stage 5: Final verification"
echo "========================================="

claude -p "You are a senior code reviewer for Akto.

Review ALL changes made in this session by checking git diff:
1. Run \`git diff\` to see all changes
2. Check for: compilation errors, missing imports, inconsistent naming, 
   security issues (missing auth checks), broken patterns
3. Run \`~/Downloads/apache-maven-3.8.1/bin/mvn compile -pl apps/dashboard -am\` to verify backend compiles
4. List any issues found and fix them.

If everything looks good, write a summary of all changes to .claude/workspace/implementation/SUMMARY.md" \
  --allowedTools "Read,Write,Glob,Grep,Bash" \
  --output-format json > .claude/workspace/review-output.json

echo "  ✓ Review done"
echo ""
echo "========================================="
echo "Complete! Check:"
echo "  - Spec:         .claude/workspace/specs/PRD.md"
echo "  - Architecture: .claude/workspace/architecture/DESIGN.md"
echo "  - Tasks:        .claude/workspace/architecture/tasks.md"
echo "  - Summary:      .claude/workspace/implementation/SUMMARY.md"
echo "  - Git diff:     git diff"
echo "========================================="

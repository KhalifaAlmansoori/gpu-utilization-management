# Team workflow

Our GitHub history is graded: a member with no visible individual progress has their project
mark halved. Commit counts and screenshots alone are not enough, so the history must show each
person building their own part over time.

## Rules

1. **Commit from your own GitHub account**, on your own computer. Never commit for someone else.
2. **Commit small and often** (at least every working session), not one big commit at the end.
3. **Pull before you start working**, push when you stop.
4. **Write clear messages** that say what changed:
   - Good: `Add Job state checks for start(), complete() and cancel()`
   - Bad: `update`, `final`, `asdf`
5. **Stay in your own files** where possible, to avoid merge conflicts. Agree on class and
   method names before writing code that depends on someone else's part.
6. **Do not commit** compiled files (`out/`, `bin/`, `*.class`) or IDE settings. The `.gitignore`
   already excludes them.
7. **Keep the build working**: make sure the project compiles before you push.

## Daily routine

```bash
git pull                      # get teammates' changes
# ... work ...
git add <files you changed>
git commit -m "Describe what you did"
git push
```

If `git push` is rejected, run `git pull`, fix any conflict, then push again.

## Package rule

- `model` and `logic` never import `javax.swing`.
- Button listeners in `ui` only read the fields, call one method in `logic`, and show the result.

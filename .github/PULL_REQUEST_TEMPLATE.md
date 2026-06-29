<!-- Thanks for contributing! Keep PRs focused and explain the *why*. -->

## What & why

What does this change, and what problem does it solve?

## How

Brief notes on the approach — especially any new seam, extension point, or
persisted shape.

## Checklist

- [ ] `./gradlew :app:testDebugUnitTest` passes
- [ ] `./gradlew :app:ktlintCheck` passes (ran `ktlintFormat` if needed)
- [ ] `./gradlew :app:assembleDebug` builds
- [ ] New pure logic has unit tests; new persisted shapes round-trip and
      degrade gracefully on bad data
- [ ] The change reads clearly to someone seeing it cold (legibility is the spec)

By submitting this PR, I agree my contribution is licensed under the project's
[Apache 2.0 License](../LICENSE).

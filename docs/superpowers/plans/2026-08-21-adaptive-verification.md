# Adaptive Verification and One-Time CAPTCHA Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a tested LimboFilter fork with replay-resistant randomized physics verification and single-use advanced Minecraft map CAPTCHAs.

**Architecture:** Pure Java physics and challenge state-machine classes remain independent of Velocity, while `BotFilterSessionHandler` adapts LimboAPI callbacks and packets. The existing renderer is augmented by deterministic advanced challenge families, and a bounded queue replaces complete-image replay in the enabled one-time mode.

**Tech Stack:** Java 21, Gradle, JUnit Jupiter 5, LimboAPI 1.1.27, Velocity 3.5.0-SNAPSHOT, AWT image rendering.

**Spec:** `docs/superpowers/specs/2026-08-21-adaptive-verification-design.md`

## Global Constraints

- Preserve AGPL-3.0 headers and do not copy Sonar source code.
- Keep `captcha-generator.prepare-captcha-packets: false` when one-time CAPTCHA is enabled.
- Production mode defaults to `SHADOW`; Geyser uses the legacy/CAPTCHA path.
- Production randomness uses `SecureRandom`; tests inject deterministic `RandomGenerator` instances.
- No production deployment or runtime configuration mutation is part of this plan.
- Every production behavior is introduced only after its focused test has failed for the expected reason.

---

### Task 1: Test harness and immutable motion model

**Files:**
- Modify: `build.gradle`
- Create: `src/main/java/net/elytrium/limbofilter/verification/MotionVector.java`
- Create: `src/main/java/net/elytrium/limbofilter/verification/MotionSample.java`
- Create: `src/main/java/net/elytrium/limbofilter/verification/PhysicsProfile.java`
- Create: `src/test/java/net/elytrium/limbofilter/verification/MotionVectorTest.java`
- Create: `src/test/java/net/elytrium/limbofilter/verification/PhysicsProfileTest.java`

**Interfaces:**
- Produces: `record MotionVector(double x, double y, double z)` with `add`, `scale`, `isFinite`, and `distanceTo`.
- Produces: `record MotionSample(int tick, MotionVector position, MotionVector velocity, boolean onGround)`.
- Produces: `record PhysicsProfile(double gravity, double verticalDrag, double horizontalDrag, double positionTolerance, double collisionTolerance, int maxPacketGapTicks, boolean impulseSupported)` plus `javaLegacy(double, double, int)` and `javaModern(double, double, int)` factories accepting position tolerance, collision tolerance, and maximum packet gap.

- [ ] Add `testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")` and `test { useJUnitPlatform() }`.
- [ ] Write tests proving vector operations, non-finite rejection, and profile bound validation.
- [ ] Run `./gradlew test --tests '*MotionVectorTest' --tests '*PhysicsProfileTest'` and require missing-type compilation failure.
- [ ] Implement the three immutable types with constructor validation.
- [ ] Re-run the focused tests and require PASS.
- [ ] Commit the focused increment as `test: add adaptive verification model`.

### Task 2: Vanilla trajectory prediction and envelopes

**Files:**
- Create: `src/main/java/net/elytrium/limbofilter/verification/VanillaPhysics.java`
- Create: `src/main/java/net/elytrium/limbofilter/verification/TrajectoryEnvelope.java`
- Create: `src/main/java/net/elytrium/limbofilter/verification/TrajectoryMatch.java`
- Create: `src/test/java/net/elytrium/limbofilter/verification/VanillaPhysicsTest.java`
- Create: `src/test/java/net/elytrium/limbofilter/verification/TrajectoryEnvelopeTest.java`

**Interfaces:**
- Consumes: `MotionVector`, `MotionSample`, `PhysicsProfile`.
- Produces: `VanillaPhysics.next(MotionSample previous, PhysicsProfile profile, double platformTopY)`.
- Produces: `TrajectoryEnvelope.match(MotionSample previous, MotionVector actualPosition, boolean onGround, PhysicsProfile profile, double platformTopY)` returning `TrajectoryMatch(boolean matched, int advancedTicks, MotionSample predicted, boolean collision)`.

- [ ] Write known-sequence tests for `-0.0784`, successive vertical drag, horizontal `0.91` drag, and collision clamping.
- [ ] Run focused tests and require missing-class failure.
- [ ] Implement one-tick prediction with finite-value checks and platform collision.
- [ ] Re-run `VanillaPhysicsTest` and require PASS.
- [ ] Write envelope tests for one tick, four skipped ticks, excessive gap, wrong X/Z, early ground, and collision tolerance.
- [ ] Run the envelope tests and require missing-class failure.
- [ ] Implement monotonic bounded matching across `1..maxPacketGapTicks`.
- [ ] Re-run both focused suites and require PASS.
- [ ] Commit as `feat: model bounded vanilla trajectories`.

### Task 3: Random challenge programs and replay-resistant session state

**Files:**
- Create: `src/main/java/net/elytrium/limbofilter/verification/ChallengePhase.java`
- Create: `src/main/java/net/elytrium/limbofilter/verification/ChallengeInstruction.java`
- Create: `src/main/java/net/elytrium/limbofilter/verification/ChallengeProgram.java`
- Create: `src/main/java/net/elytrium/limbofilter/verification/ChallengeProgramFactory.java`
- Create: `src/main/java/net/elytrium/limbofilter/verification/VerificationResult.java`
- Create: `src/main/java/net/elytrium/limbofilter/verification/AdaptiveVerificationSession.java`
- Create: `src/test/java/net/elytrium/limbofilter/verification/ChallengeProgramFactoryTest.java`
- Create: `src/test/java/net/elytrium/limbofilter/verification/AdaptiveVerificationSessionTest.java`
- Create: `src/test/java/net/elytrium/limbofilter/verification/SonarBaselineResistanceTest.java`

**Interfaces:**
- Produces: `enum ChallengePhase { FALL_COLLISION, IMPULSE_COLLISION }`.
- Produces: `record ChallengeInstruction(ChallengePhase phase, int teleportId, MotionVector start, MotionVector initialVelocity, double platformTopY, double platformHalfWidth)`.
- Produces: `ChallengeProgramFactory.create(PhysicsProfile, RandomGenerator, int phases)`.
- Produces: `AdaptiveVerificationSession.start()`, `confirmTeleport(int)`, `move(MotionVector, boolean)`, `currentInstruction()`, and terminal `VerificationResult` values.

- [ ] Write factory tests proving coordinate, nonce, velocity, phase-count, and safety bounds plus deterministic seeded output.
- [ ] Run them and require missing-class failure.
- [ ] Implement immutable phase/program types and the factory.
- [ ] Re-run factory tests and require PASS.
- [ ] Write session tests for valid fall/impulse programs, wrong nonce, movement before confirm, replay, NaN, wrong collision, sample cap, and timeout.
- [ ] Run them and require missing-class failure.
- [ ] Implement the state machine using `TrajectoryEnvelope`, with no Velocity imports.
- [ ] Re-run session tests and require PASS.
- [ ] Add a 10,000-seed test that feeds one fixed Sonar-style fall transcript to each randomized program and asserts at least 99% rejection.
- [ ] Run the baseline test and require it to fail before the randomized mismatch checks are complete, then make the minimum correction.
- [ ] Run all verification tests and require PASS.
- [ ] Commit as `feat: add randomized adaptive challenge programs`.

### Task 4: Deterministic advanced CAPTCHA families

**Files:**
- Create: `src/main/java/net/elytrium/limbofilter/captcha/advanced/CaptchaFamily.java`
- Create: `src/main/java/net/elytrium/limbofilter/captcha/advanced/RenderedCaptcha.java`
- Create: `src/main/java/net/elytrium/limbofilter/captcha/advanced/AdvancedCaptchaRenderer.java`
- Create: `src/test/java/net/elytrium/limbofilter/captcha/advanced/AdvancedCaptchaRendererTest.java`

**Interfaces:**
- Produces: `enum CaptchaFamily { TEXT, ARITHMETIC, MARKED_GLYPHS }`.
- Produces: `record RenderedCaptcha(CaptchaFamily family, String answer, BufferedImage image)`.
- Produces: `AdvancedCaptchaRenderer.render(CaptchaFamily, RandomGenerator)` for 128x128 challenges.

- [ ] Write tests requiring all three families to produce 128x128 images, normalized non-empty answers, no confusable glyphs, deterministic seeded hashes, and different hashes across seeds.
- [ ] Run the focused suite and require missing-class failure.
- [ ] Implement rendering with standard Java fonts, antialiasing, per-glyph rotation/offset, bounded curves/dots, arithmetic expressions, and marker-to-glyph association.
- [ ] Re-run the focused suite and require PASS.
- [ ] Commit as `feat: add advanced captcha challenge families`.

### Task 5: Bounded single-use CAPTCHA pool

**Files:**
- Create: `src/main/java/net/elytrium/limbofilter/captcha/advanced/OneTimeCaptchaPool.java`
- Create: `src/test/java/net/elytrium/limbofilter/captcha/advanced/OneTimeCaptchaPoolTest.java`
- Create: `src/test/java/net/elytrium/limbofilter/captcha/CaptchaRefillControllerTest.java`
- Create: `src/main/java/net/elytrium/limbofilter/captcha/CaptchaRefillController.java`
- Modify: `src/main/java/net/elytrium/limbofilter/captcha/CaptchaGenerator.java`
- Modify: `src/main/java/net/elytrium/limbofilter/captcha/CaptchaHolder.java`

**Interfaces:**
- Produces: `OneTimeCaptchaPool<T>(int capacity, int lowWaterMark)`, `offer`, `acquire`, `needsRefill`, `size`, and `clear(Consumer<T>)`.
- `CaptchaGenerator.getNextCaptcha()` consumes rather than cycles when enabled and asynchronously refills using one or two low-priority workers.

- [ ] Write pool tests for capacity, single acquisition, low-water transition, FIFO behavior, clear disposal, and concurrent no-duplicate acquisition.
- [ ] Run them and require missing-class failure.
- [ ] Implement the bounded queue and re-run the pool tests.
- [ ] Add generator-focused tests around extracted package-private refill scheduling and shutdown state, using lightweight holder suppliers rather than a Velocity mock.
- [ ] Run them and require failure against the current cyclic generator.
- [ ] Refactor `CaptchaGenerator` to keep the upstream path when disabled and use the one-time pool plus `AdvancedCaptchaRenderer` when enabled.
- [ ] Ensure a wrong answer causes `BotFilterSessionHandler.sendCaptcha()` to acquire a different holder.
- [ ] Re-run CAPTCHA tests and require PASS.
- [ ] Commit as `feat: make advanced captcha challenges single use`.

### Task 6: Configuration, packet adapter, and handler integration

**Files:**
- Modify: `src/main/java/net/elytrium/limbofilter/Settings.java`
- Create: `src/main/java/net/elytrium/limbofilter/verification/AdaptiveMode.java`
- Create: `src/main/java/net/elytrium/limbofilter/protocol/packets/AdaptivePosition.java`
- Create: `src/test/java/net/elytrium/limbofilter/protocol/packets/AdaptivePositionTest.java`
- Create: `src/test/java/net/elytrium/limbofilter/AdaptiveSettingsTest.java`
- Modify: `src/main/java/net/elytrium/limbofilter/LimboFilter.java`
- Modify: `src/main/java/net/elytrium/limbofilter/handler/BotFilterSessionHandler.java`

**Interfaces:**
- Produces settings classes `ADAPTIVE_VERIFICATION` and `ONE_TIME_CAPTCHA` with the exact defaults and bounds in the spec.
- Produces `AdaptivePosition` encoding the 1.21.2+ position packet including absolute velocity fields.
- Handler selects OFF/SHADOW/ENFORCE, keeps Geyser on legacy/CAPTCHA, and runs the original falling check after any SHADOW terminal result.

- [ ] Write byte-level packet tests for 1.21.2, 1.21.5, 1.21.9, and 26.1 encoding, asserting teleport ID, coordinates, velocity, rotation, and zero relative flags.
- [ ] Run them and require missing-class failure.
- [ ] Implement `AdaptivePosition` and register its exact clientbound mappings beside LimboAPI's position mappings.
- [ ] Re-run packet tests and require PASS.
- [ ] Add settings validation tests for all documented invalid ranges and prepared-packet incompatibility.
- [ ] Run them and require failure against current settings.
- [ ] Implement settings and validation invoked after reload.
- [ ] Add precomputed stone challenge platforms to the shared Limbo world before `createLimbo`.
- [ ] Integrate session callbacks into `BotFilterSessionHandler`; keep legacy methods unchanged behind the OFF/Geyser/SHADOW fallback paths.
- [ ] Run all unit tests and compile main sources.
- [ ] Commit as `feat: integrate adaptive verification into limbo sessions`.

### Task 7: Documentation, static gates, and complete verification

**Files:**
- Modify: `README.md`
- Modify: `VERSION`
- Modify: `build.gradle`

**Interfaces:**
- Documents mode behavior, CAPTCHA families, memory invariant, configuration, test command, compatibility limits, and the defined 99% baseline.

- [ ] Add the fork feature and rollout documentation without claiming unmeasured real-world block rates.
- [ ] Set the fork version to `1.2.0-ruscrafting.1` in Gradle and `VERSION`.
- [ ] Run `./gradlew test` and require zero failures.
- [ ] Run `./gradlew check` and require checkstyle, SpotBugs, license, and tests to pass.
- [ ] Run `./gradlew shadowJar` and require a valid non-empty JAR.
- [ ] Run `unzip -t build/libs/limbofilter-1.2.0-ruscrafting.1.jar` and require success.
- [ ] Run `git diff --check`, inspect the complete diff, and verify no secrets, runtime files, or unrelated mcserver changes are present.
- [ ] Commit as `docs: document hardened LimboFilter fork`.
- [ ] Attempt to create or attach `alexey-va/LimboFilter`, push `master`, and verify remote HEAD. If GitHub authentication remains unavailable, preserve every verified local commit and report the exact external blocker without weakening local verification.

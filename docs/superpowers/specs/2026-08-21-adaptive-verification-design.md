# Adaptive Verification and One-Time CAPTCHA Design

## Purpose

This fork strengthens LimboFilter against bots that can replay or emulate the
fixed falling sequence used by existing Minecraft antibot plugins. It keeps the
verification cheap for the proxy, deterministic enough to test, tolerant of
normal packet batching, and compatible with the existing LimboFilter flow.

The implementation is independent. Sonar is used as a behavioral reference,
not as a source of copied code.

## Evidence and baseline

The open-source Sonar 2.1.50 implementation at commit
`449ff7a55df2872c3f146ed9e3b0e716611af032` verifies:

- the vanilla vertical recurrence `(previousDeltaY - 0.08) * 0.98`;
- collision with a randomly selected platform block;
- teleport confirmation and packet ordering;
- a boat gravity and input sequence;
- transaction or ping nonces.

Relevant sources:

- <https://github.com/jonesdevelopment/sonar/blob/449ff7a55df2872c3f146ed9e3b0e716611af032/common/src/main/java/xyz/jonesdev/sonar/common/verification/GravityHandler.java>
- <https://github.com/jonesdevelopment/sonar/blob/449ff7a55df2872c3f146ed9e3b0e716611af032/common/src/main/java/xyz/jonesdev/sonar/common/verification/VehicleHandler.java>
- <https://github.com/jonesdevelopment/sonar/blob/449ff7a55df2872c3f146ed9e3b0e716611af032/common/src/main/java/xyz/jonesdev/sonar/common/verification/ProtocolHandler.java>

Upstream LimboFilter 1.1.19 checks a single cached vertical fall curve. It uses
fixed X/Z coordinates, skips repeated Y packets, and accepts a configurable
`0.01` position difference. It has no collision model, per-session physics
program, or replay-resistant state machine.

A modern bot framework such as Mineflayer already implements gravity,
collision, vehicles, and many protocol versions. A single harder fall is not a
durable differentiator. The fork therefore randomizes the challenge program
and combines independent protocol and physics signals.

## Chosen approach

Each Java client receives a per-session `ChallengeProgram` generated from a
cryptographically random seed. A program contains ordered phases selected from
a version-compatible catalogue:

1. `TELEPORT_SYNC`: random coordinates and teleport nonce; the verifier rejects
   movement before the expected acknowledgement on versions that support it.
2. `FALL_COLLISION`: vanilla gravity from a randomized safe height to a
   randomized platform height. The verifier checks trajectory envelopes and
   the final on-ground collision rather than a single exact packet cadence.
3. `IMPULSE_COLLISION`: on protocol 1.21.2 and newer, a teleport packet carries
   a random bounded velocity vector. The verifier checks horizontal and
   vertical motion plus the final collision envelope.

The catalogue intentionally excludes human timing, mouse entropy, or artificial
jitter heuristics. Bots can synthesize those signals and real clients behind
ViaVersion or unstable networks can violate them.

For protocols older than 1.21.2, the program uses randomized teleport and fall
collision phases without the velocity phase. Geyser connections use CAPTCHA
fallback because Java and Bedrock movement semantics differ. The pre-existing
LimboFilter checks remain available as a compatibility fallback.

## Physics model

Physics is implemented as pure functions and immutable value objects, separated
from Velocity and LimboAPI:

- `MotionVector`: X/Y/Z components and vector operations.
- `MotionSample`: tick index, position, delta, and on-ground state.
- `PhysicsProfile`: gravity, drag, horizontal drag, per-axis tolerance, packet
  gap limit, and supported capabilities for a protocol family.
- `VanillaPhysics`: predicts one or more ticks from the previous state.
- `TrajectoryEnvelope`: validates a sample against every allowed skipped-tick
  prediction from one through the configured packet gap.
- `ChallengeProgramFactory`: selects safe parameters from a supplied random
  source and emits an immutable program.
- `ChallengeSession`: advances the phase state machine and returns structured
  outcomes with machine-readable failure reasons.

Production uses `SecureRandom`. Tests inject a seeded `RandomGenerator`, making
every program and trajectory reproducible.

The verifier never accepts an arbitrary nearest point on an unlimited curve.
It advances monotonically, caps skipped ticks, caps total samples and duration,
and rejects NaN, infinity, backwards phase transitions, wrong teleport nonces,
wrong X/Z motion, early on-ground flags, and replayed transcripts.

## Integration

`BotFilterSessionHandler` remains the LimboAPI entry point but delegates the new
logic to `AdaptiveVerificationSession`. The handler is responsible only for:

- sending the packets described by the current phase;
- forwarding movement, on-ground, and teleport events;
- selecting `OFF`, `SHADOW`, or `ENFORCE` behavior;
- continuing to CAPTCHA or existing success/failure handling.

`SHADOW` records the adaptive result but preserves the upstream decision.
`ENFORCE` requires the adaptive session to pass. `OFF` constructs no adaptive
state. The default is `SHADOW` until a real-client compatibility pass has been
performed outside this coding task.

`test-usernames` is a case-insensitive pilot allowlist. Listed Java usernames
use `ENFORCE` regardless of the global mode, while every other connection keeps
the configured mode. Geyser and `ONLY_CAPTCHA` paths ignore this override.

The fork adds its own clientbound adaptive position packet because LimboAPI's
1.21.2+ position packet currently hardcodes the velocity fields to zero. The
packet uses LimboAPI's existing registration boundary and is prepared only for
small static scaffolding; per-session nonces and vectors are written as ordinary
heap-backed packet objects.

## One-time CAPTCHA

The existing visual painter is retained, avoiding a new web-oriented CAPTCHA
dependency. NanoCaptcha was evaluated as the best external Java option because
it is small, BSD-3-Clause licensed, builder-based, and produces
`BufferedImage`; however, it does not solve replay or pool reuse. Importing it
would duplicate LimboFilter's existing rendering primitives without providing
the security property the fork needs.

The fork integrates a bounded `OneTimeCaptchaPool` into `CaptchaGenerator`.
Every acquired challenge is removed permanently. One or two background workers
refill the pool to a configured high-water mark and stop cleanly on reload. The
pool stores map-palette byte arrays on heap and never retains prepared Netty
packet buffers.

Three challenge families are supported:

- `TEXT`: non-confusable letters and digits with independent glyph transforms.
- `ARITHMETIC`: a short addition or subtraction expression with a numeric
  answer.
- `MARKED_GLYPHS`: enter only glyphs carrying a specified visual marker. This
  requires OCR plus spatial association rather than plain text extraction.

All answers are normalized by a family-specific policy. A challenge is
single-use even when the player answers incorrectly; a retry gets a newly
generated image and answer.

The default pool size is `128`, refill low-water mark is `32`, generation uses
one low-priority worker, and acquisition fails closed to the existing
"captcha not ready" message if the pool is exhausted. The existing runtime
invariant `captcha-generator.prepare-captcha-packets: false` remains intact.

## Configuration

New settings live under two top-level sections in LimboFilter's generated YAML:

```yaml
adaptive-verification:
  mode: SHADOW
  test-usernames: []
  max-packet-gap-ticks: 4
  max-samples-per-phase: 160
  max-session-millis: 12000
  phases-per-session: 3
  impulse-enabled: true
  position-tolerance: 0.035
  collision-tolerance: 0.0625

one-time-captcha:
  enabled: true
  pool-size: 128
  refill-low-water-mark: 32
  generator-threads: 1
  families:
    - TEXT
    - ARITHMETIC
    - MARKED_GLYPHS
```

Invalid bounds fail reload with a precise exception. `pool-size` is limited to
`16..512`, generator threads to `1..2`, phase count to `2..3`, and tolerances
must be finite and positive. Enabling prepared CAPTCHA packets together with
the one-time pool fails reload rather than risking direct-memory exhaustion.

## Observability and failure handling

Adaptive verification produces one terminal result:

- `PASS`;
- `FAIL_PROTOCOL`;
- `FAIL_TRAJECTORY`;
- `FAIL_COLLISION`;
- `FAIL_REPLAY`;
- `TIMEOUT`;
- `UNSUPPORTED`.

Debug logging contains the player, protocol, mode, and terminal result, but
never the random seed, CAPTCHA answer, or complete expected trajectory. Normal
operation does not log individual movement packets.

If the CAPTCHA worker throws, the error is logged once per refill cycle and the
last healthy pool remains usable. Reload swaps in a fully initialized provider
before shutting down the previous provider.

## Testing and success criteria

JUnit 5 tests cover pure behavior without launching Velocity:

1. Known vanilla gravity and impulse trajectories pass for each supported
   profile.
2. Wrong teleport nonce, illegal order, NaN/infinity, excessive packet gaps,
   early ground, wrong collision height, and mutated deltas fail with the
   expected reason.
3. Replaying a transcript against a program produced from a different seed
   fails.
4. Across 10,000 deterministic seeds, a fixed Sonar-style falling transcript
   is rejected in at least 99% of generated programs. This is a defined
   regression baseline, not a claim that 99% of all real attackers are blocked.
5. Every CAPTCHA family produces a 128x128 map, a valid normalized answer, and
   deterministic output under an injected random source.
6. A challenge can be acquired only once; retries receive distinct IDs,
   answers or pixel hashes; pool bounds hold under concurrent acquisition.
7. Generated challenge data contains no `PreparedPacket` or Netty `ByteBuf`.
8. `./gradlew test`, `./gradlew check`, and `./gradlew shadowJar` pass on Java
   21.

No production deployment is part of this scope. A later rollout must first run
the fork in `SHADOW`, test real Java clients across supported versions, confirm
Geyser fallback, and inspect direct-memory behavior before enabling `ENFORCE`.

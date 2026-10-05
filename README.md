# Minecraft Input/Output (MCio)

### [MCio mod](https://github.com/twoturtles/MCio) | [mcio_ctrl](https://github.com/twoturtles/mcio_ctrl) | [Documentation](https://github.com/twoturtles/mcio_ctrl/wiki) | [Discord](https://discord.gg/PBfdc27h4q)

MCio is a Fabric mod that exposes a network interface to Minecraft for AI research. It accepts keyboard/mouse input and sends back video frames and game state over ZeroMQ.

## Features

* Python library ([mcio_ctrl](https://github.com/twoturtles/mcio_ctrl)) with Gymnasium environments
* Faster than real-time performance (>13x on an M3 laptop)
* Works with performance mods like Sodium
* Supports resource packs for varied training environments
* Connect/reconnect agents without restarting Minecraft
* Decoupled ZMQ protocol for easy integration
* Synchronous mode for fast training
* Asynchronous mode for real-time human/AI interaction
* Headless mode with GPU acceleration
* [VPT and STEVE-1 support](https://github.com/jxiong21029/mcio-vpt-example) on modern Minecraft with [Sodium](https://modrinth.com/mod/sodium)

## Performance

Synchronous mode is the fast path. Two optional features on top of it are off unless a client asks for
them, and change nothing when unused (protocol version unchanged):

* **Skip the frame for steps that discard it** (`send_frame` in the action packet). The client skips the
  framebuffer readback and the frame serialization for that step; the observation is still produced and
  sent (stats, health, hits/crits, inventory, server tick) with an empty frame and the previous
  `frame_sequence`. `mcio_ctrl` sets the field with `options={"send_frame": False}`; the field is not
  put on the wire otherwise, so older clients are unaffected. An older mod rejects the unknown field,
  so install this build on every instance a frame-skipping client talks to.
* **Per-phase timers**: `MCIO_PROFILE=true` logs one `PROFILE phase=<name> n=<count> mean_us=<...>
  max_us=<...>` line every 2 s (client tick, action receive/process, frame capture alloc/readback/
  callback, observation collect/pack/send, server gate/idle/tick/push). Off by default; the
  instrumented paths cost a single static boolean read when disabled.

Rendering is a large share of the per-tick time on a training client: Sodium (`0.6.13+mc1.21.3`)
measured +30 % single-player and +40 % per duel client. Apply it to **all** training instances or none,
because it shifts rendered pixels slightly and mixed setups fail frame-parity checks.

No JVM flags are needed: `-Xms/-Xmx`, G1 pause tuning and `-XX:+AlwaysPreTouch` measured neutral.

## Links

* [Documentation / Wiki](https://github.com/twoturtles/mcio_ctrl/wiki)
* [MCio mod](https://github.com/twoturtles/MCio) ([Modrinth](https://modrinth.com/mod/mcio))
* [mcio_ctrl](https://github.com/twoturtles/mcio_ctrl) ([PyPI](https://pypi.org/project/mcio_ctrl/))

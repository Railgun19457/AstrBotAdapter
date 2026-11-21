# platform-fabric

Fabric support targets Dedicated Server on Minecraft 26.1.2.

Implemented scope:

- Fabric Loom build with `fabric.mod.json`.
- Dedicated server lifecycle bootstrap.
- Server-thread scheduler adapter.
- Chat, join and leave event forwarding.
- Broadcast and private messages.
- Online player list and online player detail basics.
- Server status and snapshot.
- Console and player command execution.
- `/astrbot` management command registration.
- Standalone mode by default and Velocity backend mode through `mode: velocity_backend`, `ASTRBOT_ADAPTER_MODE`, or `-Dastrbotadapter.mode=velocity_backend`.

Deferred scope:

- Offline player history.
- Advanced log tail implementation.
- Multi-Minecraft-version compatibility layer.

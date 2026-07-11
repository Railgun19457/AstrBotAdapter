# AstrBotAdapter v3

Minecraft server side adapter for AstrBot.

This branch is the v3 rewrite. It follows the shared design documents in `../docs` and keeps the user-visible plugin name `AstrBotAdapter`.

## Modules

- `common-util`: shared utilities.
- `common-protocol`: protocol envelope, enums and validation model.
- `common-transport`: transport/session abstractions.
- `platform-spi`: platform-independent Minecraft service interfaces.
- `common-core`: runtime orchestration and capability calculation.
- `platform-bukkit`: Bukkit/Paper/Spigot entrypoint.
- `platform-folia`: Folia entrypoint.
- `platform-velocity`: Velocity entrypoint.
- `platform-fabric`: Fabric server mod placeholder module.

## Build

```powershell
gradle build
```

Java 17 bytecode is used for Minecraft compatibility.

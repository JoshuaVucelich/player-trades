# Client source set

This directory is intentionally empty.

Loom's split source sets keep client-only Minecraft classes off the common compile classpath. Player Trades is a dedicated-server mod: `fabric.mod.json` sets `"environment": "server"`, and there is no client entrypoint. Do not add client classes, mixins, or screens here. Bedrock and Java clients both use the vanilla chest menu the server opens.

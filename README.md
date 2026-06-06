# AlexInd's Server Skins

A client-side Minecraft 1.7.10 Forge mod that replaces player skins with per-player skins fetched from an API.

## How it works

1. For each player in the world, a GET request is sent to the configured API URL with the player's nickname
2. The API returns a JSON response with a skin URL
3. The mod downloads the skin PNG and applies it to that specific player via reflection

## Configuration

After first launch, edit `config/skinreplacer.cfg`:

```
general {
    S:apiUrl=https://node1.desert-chat.ru/api/minecraft/textures/%s
}
```

Use `%s` as the placeholder for the player nickname.

## Build

```
./gradlew clean build
```

Output: `build/libs/SkinReplacer-1.0.0.jar`

## Requirements

- Minecraft 1.7.10
- Forge 10.13.4.1614+
- Java 8

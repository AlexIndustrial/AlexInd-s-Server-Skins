# AlexInd's Server Skins

A client-side Minecraft 1.7.10 Forge mod that replaces player skins with per-player skins fetched from an API.

## How it works

1. For each player in the world, a GET request is sent to the configured API URL with the player's nickname
2. The API returns a JSON response with a skin URL
3. The mod downloads the skin PNG and applies it to that specific player via reflection

## Configuration

After first launch, edit `config/alexinds_server_skins.cfg`:

```
general {
    S:apiUrl=http://localhost:8000/%s
}
```

Use `%s` as the placeholder for the player nickname.

## API

The mod sends a GET request to the configured URL (with `%s` replaced by the player's nickname).
The server must return JSON with the following structure:

```json
{
    "SKIN": {
        "url": "https://example.com/path/to/skin.png",
        "metadata": {}
    }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `SKIN.url` | string | Direct URL to the player's skin PNG (64x64 or 64x32) |
| `SKIN.metadata` | object | Reserved for future use, pass an empty object `{}` |

The skin PNG must be accessible via direct GET request (no redirects, no auth).

## Example server

A minimal Python server is provided in `example-server/server.py`:

```
python example-server/server.py
```

Place 64x64 PNG skins in `example-server/skins/` named as `<nickname>.png` (e.g., `Notch.png`).
The server will serve both the JSON endpoint and the skin files.

Configure the mod to use:
```
general {
    S:apiUrl=http://your-ip:8000/%s
}
```

## Build

```
./gradlew clean build
```

Output: `build/libs/SkinReplacer-1.0.0.jar`

## Requirements

- Minecraft 1.7.10
- Forge 10.13.4.1614+
- Java 8

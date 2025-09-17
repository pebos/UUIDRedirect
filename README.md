# UUIDRedirect

**UUIDRedirect** is a Velocity proxy plugin that automatically redirects players with specific UUIDs (and/or usernames) to designated servers. It also prevents redirected players from switching to other servers, making it perfect for private lobbies, staff servers, or pre-assigned game modes.

## Features

- Redirect players on login to a pre-configured server.
- Supports redirecting by **UUID** and/or **username**.
- Prevents redirected players from switching to other servers.
- Automatically updates player usernames in the configuration if they change.
- Simple configuration using a JSON file.

## Installation

1. Place the `UUIDRedirect.jar` file in your Velocity `plugins` folder.
2. Start Velocity to generate the default configuration file: `plugins/UUIDRedirect/config.json`.
3. Stop Velocity and edit `config.json` to configure your redirects.

## Configuration

The configuration file `config.json` maps player UUIDs (or usernames if UUID is unavailable) to a server. Example:

```json
{
  "11111111-1111-1111-1111-111111111111": {
    "username": "ExamplePlayer",
    "server": "survival"
  },
  "": {
    "username": "NameOnlyPlayer",
    "server": "lobby"
  }
}

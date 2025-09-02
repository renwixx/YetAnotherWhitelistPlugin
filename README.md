# YAWL (Yet Another Whitelist Plugin)

**YAWL** is an incredibly simple, lightweight, and efficient whitelist plugin designed exclusively for the **Velocity** proxy. If you need a no-fuss, easy-to-manage whitelist solution that just works, this is the plugin for you.

## 🚀 Overview

The core philosophy of YAWL is **simplicity**. In a world of complex plugins, YAWL stands out by doing one thing and doing it well. It manages a server whitelist through a plain text file (`whitelist.txt`), making it easy to view, edit, and manage without needing databases or complicated commands. It's built to be as performant and unobtrusive as possible, ensuring zero impact on your server's performance.

## ✨ Why Choose YAWL?

  * **⚡ Lightweight:** The plugin has a minimal footprint. It's written to be extremely efficient and won't add any bloat or lag to your proxy.
  * **✏️ Simple Text-Based Management:** All whitelisted players are stored in a simple `whitelist.txt` file. You can edit this file directly and reload the plugin in-game.
  * **⚙️ Straightforward Configuration:** A clean `config.toml` file allows you to toggle the whitelist, change message languages, and set case sensitivity with ease.
  * **🌍 Multi-Language Support:** YAWL comes with multiple pre-packaged languages (`en`, `ar`, `de`, `es`, `fr`, `ja`, `ru`, `uk`, `zh-cn`). It can even **automatically display messages in a player's client language**\!
  * **🔄 Live Reload:** No need to restart your entire proxy. A simple command reloads the configuration and the whitelist instantly.
  * **🔒 Permissions-Ready:** Fine-grained permission nodes give you complete control over who can manage the whitelist.

## 📦 Installation

1.  Download the latest version of the plugin from the [Releases](https://github.com/renwixx/YetAnotherWhitelistPlugin/releases) page or Modrinth (currently unavialable).
2.  Place the downloaded `.jar` file into the `plugins` folder of your Velocity proxy.
3.  Start or restart your proxy.
4.  The default configuration (`config.toml`), locales, and an empty `whitelist.txt` file will be generated in `plugins/yawl`. Edit them to your liking\!

## ⚙️ Configuration

The configuration is handled in the `plugins/yawl/config.toml` file.

```toml
[settings]
# Enable or disable the whitelist.
enabled = true

# Sets the default language for plugin messages (e.g., "en", "ru").
locale = "en"

# If true, the plugin will try to use the player's client language if a translation is available.
use-client-locale = false

# If true, player names are case-sensitive ("Player" and "player" are different).
# It is recommended to keep this 'false'.
case-sensitive = false
```

## 💬 Commands

All commands start with `/yawl`.

| Command                          | Description                               |
| -------------------------------- | ----------------------------------------- |
| `/yawl`                          | Displays the plugin help message.         |
| `/yawl add <player>`             | Adds a player to the whitelist.           |
| `/yawl remove <player>`          | Removes a player from the whitelist.      |
| `/yawl list`                     | Shows a list of all whitelisted players.  |
| `/yawl reload`                   | Reloads the config and `whitelist.txt`.   |

## 🔑 Permissions

Grant these permissions to your staff groups to control who can manage the whitelist.

| Permission             | Description                                                   |
| ---------------------- | ------------------------------------------------------------- |
| `yawl.bypass`          | Allows a player to join the server even if not on the whitelist. |
| `yawl.command.add`     | Allows using the `/yawl add` command.                         |
| `yawl.command.remove`  | Allows using the `/yawl remove` command.                      |
| `yawl.command.list`    | Allows using the `/yawl list` command.                        |
| `yawl.command.reload`  | Allows using the `/yawl reload` command.                      |

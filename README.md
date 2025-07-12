# NoDotNameDuplicates

A Spigot plugin that prevents Bedrock and Java players from logging in at the same time with the same or linked accounts. It also synchronizes player `.dat` files between them.

> ⚠️ **Requires [GeyserMC](https://geysermc.org/)** to allow Bedrock clients to connect to the Java server.

---

## 🔧 Features

- ⛔ Prevents simultaneous login of Java and Bedrock players with the same name (e.g., `Steve` and `.Steve`)
- 🔗 Supports manually linked Java ↔ Bedrock accounts using the `config.yml`
- 🔁 Automatically syncs the newer `.dat` file to the other account
- 🌍 Detects and uses the server's default world for player data
- 🐞 Optional debug logging

---

## 📦 Installation

1. **Install GeyserMC** on your Java server to support Bedrock players.
2. Download [latest Jar](https://github.com/JMiahMan1/NoDotNameDuplicates/releases/download/latest/NoDotNameDuplicates-latest.jar) file or clone this plugin source:

   ```bash
   git clone https://github.com/your-username/NoDotNameDuplicates.git
   cd NoDotNameDuplicates
   ```

3. If compiling, compile with Maven (requires Java 17+):

   ```bash
   mvn clean package
   ```

4. Copy the generated JAR from the `target/` directory (or from the download link) into your server’s `plugins/` folder:

   ```
   target/NoDotNameDuplicates-1.0.6.jar
   ```

5. Start your server. The plugin will generate a default `config.yml`.

---

## ⚙️ Configuration

Located at: `plugins/NoDotNameDuplicates/config.yml`

```yaml
debug: false

linkedPlayers:
  Steve: BedrockSteve
  Alice: Super_Alice
```

- `debug`: Enable or disable debug logging
- `linkedPlayers`: Map a Java player name to its associated Bedrock counterpart. These names are case-insensitive and spaces are represented by an underscore (ie. Super Alice => Super_Alice).
---

## 🧠 How It Works

- **On Login:**
  - Checks if a player with the same base name (ignoring a leading dot) or a manually linked name is already online
  - Denies login if the linked account is online
  - Syncs the `.dat` file from the most recently updated version to the older one

- **On Logout:**
  - Syncs the most recently modified `.dat` file to the older one if needed

---

## 🛠 Development Notes

### Requirements

- Java 17+
- Maven
- Spigot API (`1.20.1-R0.1-SNAPSHOT`)
- GeyserMC (installed separately)

### Project Structure

```
src/
  main/
    java/
      com/
        example/
          nodotnameduplicates/
            NoDotNameDuplicates.java
    resources/
      plugin.yml
      config.yml
```

### Build

```bash
mvn clean package
```

The resulting `.jar` file will be placed in the `target/` folder.

---

## 📜 License

This project is licensed under the [MIT License](LICENSE). You are free to use, modify, and distribute this plugin.

---

## 🙋 Support

For questions, suggestions, or contributions, please open an issue or submit a pull request on GitHub.

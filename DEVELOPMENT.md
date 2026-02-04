# Development Guide

This guide covers setting up a development environment for the digital-signature Confluence plugin.

## Quick Reference

| Property | Value |
|----------|-------|
| Confluence Version | 9.5.4 |
| AMPS Version | 9.5.4 |
| Java | 11 |
| Spring | 6.1.20 |
| Build Tool | Maven |

### Essential Commands

```bash
atlas-run                    # Start Confluence with plugin
atlas-debug                  # Start with debugger on port 5005
atlas-package                # Build and hot-reload
mvn package -DskipTests      # Fast build
mvn test                     # Run tests
```

## Prerequisites

### SDKMAN (Java Version Manager)

This project requires Java 11. We use [SDKMAN](https://sdkman.io/) to manage Java versions per-project.

**Install SDKMAN:**
```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
```

**Install Java 11:**
```bash
sdk install java 11.0.25-tem
```

**Enable automatic version switching:**

Edit `~/.sdkman/etc/config` and set:
```
sdkman_auto_env=true
```

Or run:
```bash
sed -i '' 's/sdkman_auto_env=false/sdkman_auto_env=true/' ~/.sdkman/etc/config
```

This project includes a `.sdkmanrc` file. With `auto_env` enabled, SDKMAN automatically switches to Java 11 when you enter the project directory.

**Note:** After changing the config, open a new terminal or reload SDKMAN in your current shell:
```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
```

**Verify:**
```bash
cd src && cd ..
java -version
# Should show: openjdk version "11.x.x"
```

### Atlassian SDK

The Atlassian Plugin SDK provides tools for building and testing Confluence plugins.

**macOS (Homebrew):**
```bash
brew tap atlassian/tap
brew install atlassian/tap/atlassian-plugin-sdk
```

**Other platforms:** See [Atlassian SDK Installation Guide](https://developer.atlassian.com/server/framework/atlassian-sdk/set-up-the-atlassian-plugin-sdk-and-build-a-project/)

**Verify installation:**
```bash
atlas-version
```

### Confluence License

You need a Confluence development license (free):

1. Go to [my.atlassian.com](https://my.atlassian.com)
2. Sign in or create an account
3. Navigate to Developer Resources > Licenses
4. Generate a Confluence (Data Center) evaluation license

## Getting Started with Atlassian SDK

### First Run

```bash
# From the project root directory
atlas-run
```

This will:
- Download Confluence and dependencies (first run takes a while)
- Start Confluence on port 1990
- Deploy the plugin automatically

**Access Confluence:** http://localhost:1990/confluence

**Default credentials:** admin / admin

### Initial Setup

1. Open http://localhost:1990/confluence
2. Enter your Confluence license when prompted
3. Complete the setup wizard (choose "Example Site" for quick setup)
4. Create a test space to work with

### QuickReload Workflow

The project has QuickReload enabled. After making code changes:

```bash
atlas-package
```

The plugin will automatically reload without restarting Confluence.

## Building the Plugin

### Standard Build

```bash
mvn package
```

**Output:** `target/digital-signature-9.0.1.jar`

### Fast Build (Skip Tests)

```bash
mvn package -DskipTests
```

### Run Tests Only

```bash
mvn test
```

### Clean Build

```bash
mvn clean package
```

## Development Workflow

### Typical Development Cycle

1. Make code changes
2. Run `atlas-package` (or `mvn package -DskipTests`)
3. QuickReload picks up changes automatically
4. Test in browser at http://localhost:1990/confluence

### Key Source Files

| File | Purpose |
|------|---------|
| `src/main/java/.../DigitalSignatureMacro.java` | Main macro implementation |
| `src/main/java/.../rest/DigitalSignatureService.java` | REST API endpoints |
| `src/main/java/.../Signature2.java` | Signature data model |
| `src/main/resources/atlassian-plugin.xml` | Plugin descriptor |
| `src/main/resources/templates/macro.vm` | Main Velocity template |
| `src/main/resources/digital-signature.properties` | i18n strings |

### Modifying Velocity Templates

Templates are in `src/main/resources/templates/`:
- `macro.vm` - Main signature panel
- `email.vm` - Email notifications
- `export.vm` - PDF export

After template changes, run `atlas-package` to reload.

### Adding i18n Strings

Edit `src/main/resources/digital-signature.properties`:

```properties
com.baloise.confluence.digital-signature.mykey=My Value
```

Translations: `digital-signature_de.properties`, `digital-signature_fr.properties`, etc.

## VS Code Setup

### Required Extensions

Install these extensions:

1. **Extension Pack for Java** (vscjava.vscode-java-pack)
   - Includes Language Support, Debugger, Maven, etc.

2. **Maven for Java** (vscjava.vscode-maven)

### Project Configuration

Open the project folder in VS Code. The Java extension will automatically detect the Maven project.

If prompted, select Java 11 as the JDK.

### Debug Configuration

Create `.vscode/launch.json`:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Attach to Confluence",
      "request": "attach",
      "hostName": "localhost",
      "port": 5005
    }
  ]
}
```

### Debugging Workflow

1. Start Confluence in debug mode:
   ```bash
   atlas-debug
   ```

2. Wait for Confluence to start (watch for "Confluence is ready")

3. In VS Code:
   - Set breakpoints in your code
   - Press F5 or Run > Start Debugging
   - Select "Attach to Confluence"

4. Trigger your code (e.g., view a page with the signature macro)

### Useful Breakpoint Locations

- `DigitalSignatureMacro.execute()` - Macro rendering
- `DigitalSignatureService.sign()` - Sign action
- `DigitalSignatureService.getSignature()` - REST API calls

### Log Files

Confluence logs are in the embedded instance:
```
target/confluence/home/logs/atlassian-confluence.log
```

Tail logs in real-time:
```bash
tail -f target/confluence/home/logs/atlassian-confluence.log
```

### Adding Debug Logging

In Java code:
```java
private static final Logger log = LoggerFactory.getLogger(MyClass.class);
log.debug("Variable value: {}", myVar);
```

In Velocity templates:
```velocity
#set($debug = "Value: $myVar")
$log.debug($debug)
```

## Alternative: Docker/Podman

For production-like testing or when the SDK has compatibility issues, use Docker or Podman.

### Setup Guides

- **Docker:** See [docs/docker.md](docs/docker.md)
- **Podman (macOS):** See [docs/podman-osx.md](docs/podman-osx.md)

### Deploying Plugin to Container

Build the plugin:
```bash
mvn package -DskipTests
```

**Option 1: Copy to container**
```bash
# Docker
docker cp target/digital-signature-9.0.1.jar \
  confluence:/var/atlassian/application-data/confluence/bundled-plugins/

# Podman
podman cp target/digital-signature-9.0.1.jar \
  confluence:/var/atlassian/application-data/confluence/bundled-plugins/

# Restart to load
docker restart confluence  # or: podman restart confluence
```

**Option 2: Upload via UPM (no restart needed)**
1. Open Confluence Admin > Manage apps
2. Click "Upload app"
3. Select `target/digital-signature-9.0.1.jar`

### When to Use Docker vs SDK

| Use SDK when... | Use Docker when... |
|-----------------|-------------------|
| Active development | Testing final builds |
| Need QuickReload | Need specific Confluence version |
| Debugging | Production-like environment |
| Running integration tests | SDK has compatibility issues |

## Troubleshooting

### SDK Issues

**"Port 1990 already in use"**
```bash
# Find and kill the process
lsof -i :1990
kill -9 <PID>
```

**SDK command not found after installation**
```bash
# Add to PATH (add to ~/.zshrc or ~/.bashrc)
export PATH="/usr/local/Cellar/atlassian-plugin-sdk/*/bin:$PATH"
```

**Atlas-run fails with Java version error**

Ensure JAVA_HOME points to Java 11:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 11)
```

### Build Issues

**"Cannot resolve symbol" for Confluence classes**
```bash
mvn dependency:resolve
```

**Test failures with module access errors**

The pom.xml includes `--add-opens` flags. Ensure you're using Java 11.

### Runtime Issues

**Plugin not loading**
- Check logs: `target/confluence/home/logs/atlassian-confluence.log`
- Verify plugin in Admin > Manage apps
- Try disabling/re-enabling the plugin

**ClassCastException after update**

Clear the plugin cache:
1. Admin > Manage apps
2. Click "Clear Cache" or delete `bundled-plugins` folder
3. Restart Confluence

**Velocity template errors**

Check the velocity allowlist in `atlassian-plugin.xml`. Methods must be explicitly allowed.

### Debug Connection Issues

**Cannot connect debugger to port 5005**
- Ensure `atlas-debug` (not `atlas-run`) is running
- Check no firewall blocking port 5005
- Verify Confluence has fully started before attaching

## Additional Resources

- [Atlassian Plugin SDK Documentation](https://developer.atlassian.com/server/framework/atlassian-sdk/)
- [Confluence Plugin Development Guide](https://developer.atlassian.com/server/confluence/confluence-plugin-guide/)
- [Project Wiki](https://github.com/baloise/digital-signature/wiki)
- [Issue Tracker](https://github.com/baloise/digital-signature/issues)
- [Release Process](README.RELEASE.MD)

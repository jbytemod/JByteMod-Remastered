
# JByteMod Remastered

[![Build Status](https://ci.mdma.dev/api/badges/jbytemod/JByteMod-Remastered/status.svg)](https://ci.mdma.dev/jbytemod/JByteMod-Remastered)
![GitHub Release](https://img.shields.io/github/v/release/jbytemod/JByteMod-Remastered)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/681e07293b4c491fae53c3be6d8469fe)](https://app.codacy.com/gh/jbytemod/JByteMod-Remastered/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
![GitHub Issues or Pull Requests](https://img.shields.io/github/issues/jbytemod/JByteMod-Remastered)
![GitHub Issues or Pull Requests](https://img.shields.io/github/issues-pr/jbytemod/JByteMod-Remastered)

JByteMod Remastered is an enhanced Java bytecode editor that offers a wide array of features for decompiling, editing, and recompiling Java class files. This version includes improvements over the original JByteMod, making it a versatile tool for Java developers and enthusiasts.

## Features
-   **Android Archive Support**: Open, inspect, edit, and rebuild single- and multidex APK and Android App Bundle (`.aab`) files while preserving their resources.
-   **Advanced Bytecode Editing**: Intuitive interface for directly modifying Java bytecode.
-   **Running JVM Attachment**: Attach to a local JVM to inspect or dump loaded classes and apply compatible bytecode changes at runtime.
-   **Decompiler Integration**: Use CFR, Vineflower, Procyon, JD-Core, Koffee, and ASMifier. Packaged builds compile the latest CFR master source instead of using the old published release.
-   **Graphical Bytecode Viewer**: Visualize bytecode in a graphical format for easier comprehension.
-   **Control Flow Visualization**: Generate and view control flow diagrams of methods to understand execution flow better.
-   **Call Graph Explorer**: Explore callers and callees across the loaded archive and navigate directly to methods or their exact calling instructions.
-   **Drag and Drop Functionality**: Easily drag and drop `.jar`, `.apk`, `.aab`, and `.class` files onto the window for quick access.
-   **Search and Replace**: Effortlessly find and replace bytecode instructions.
-   **Resource Editing and Previewing**: Edit UTF-8 text resources with language-aware highlighting and preview common image formats, including PNG and WebP, directly inside archives.
-   **Constant Pool Editor**: Manage and edit constant pool entries within class files.
-   **Plugin System**: Browse, install, update, enable, and disable extensions through the built-in plugin repository.
-   **Cross-Platform Compatibility**: Compatible with Windows, macOS, and Linux operating systems.

## Installation

### Prerequisites
-   A full Java Development Kit (JDK) 21 or newer. A JRE alone is not sufficient for JVM attachment.
-   JDK 8 builds are no longer provided or supported.

### Download

1.  Obtain the latest release of JByteMod Remastered from the [releases page](https://github.com/jbytemod/JByteMod-Remastered/releases).

### Usage

1. Open a terminal or command prompt.

2. Navigate to the directory containing `JByteMod-Remastered.jar`.

3. Launch JByteMod Remastered using the following command:
    ```sh 
    java -jar JByteMod-Remastered.jar
    ```

4. Alternatively, drag and drop `.jar`, `.apk`, `.aab`, or `.class` files directly onto the JByteMod Remastered window to open them for editing.

Rebuilt APK files are automatically zip-aligned, signed, and signature-verified. Android App Bundles are rebuilt with their module-specific DEX paths, JAR-signed, and signature-verified; zip alignment does not apply to bundles. At save time, choose either your own JKS/PKCS#12 keystore or JByteMod's persistent debug key. Keystore passwords are kept only for the current save and are not stored in JByteMod's configuration. The debug key is generated as `jbytemod-debug.p12` in JByteMod's working directory on its first use, so Android archives signed by the same installation use a consistent key. Existing signatures are removed because modifying an archive invalidates them.

### Attaching to a running JVM

1. Run JByteMod with a full JDK 21 or newer.
2. Open `Utilities` > `Attach to process` and select a local JVM.
3. Browse and edit the loaded classes in the current JByteMod window.
4. Use `File` > `Apply changes` to redefine the modified classes in the target JVM.

Class redefinition is limited by the target JVM. Method-body and constant changes are generally supported, while structural changes such as adding or removing fields, methods, superclasses, or interfaces are normally rejected.

### Installing plugins

Open `Plugins` > `Manage Plugins` and select the `Plugin Repository` tab. JByteMod loads the official [`jbytemod/plugin-registry`](https://github.com/jbytemod/plugin-registry), verifies downloaded release JARs with SHA-256, and reloads installed or updated plugins without restarting the application.

Additional GitHub repositories or direct `plugins.json` URLs can be added from `Repositories...`. Third-party sources can be removed again; the official repository is built in and cannot be removed.

### Building from source

Building requires JDK 21 or newer, Maven, Git, and an internet connection:

```sh
git clone --recurse-submodules https://github.com/jbytemod/JByteMod-Remastered.git
cd JByteMod-Remastered
mvn package
```

For an existing checkout, initialize the API submodule before building:

```sh
git submodule update --init
```

The repository is a Maven reactor with three modules:

- [`jbytemod-api`](https://github.com/jbytemod/api) is the standalone plugin API, included here as a Git submodule.
- `jbytemod-agent` is the small JDK-only agent loaded into attached JVMs.
- `jbytemod-core` contains the desktop application.

The package build downloads the current CFR `master` branch, records its commit in the displayed CFR version, compiles it from source, and includes it in the final JByteMod jar.

The application is written to `jbytemod-core/target/JByteMod-Remastered-<version>.jar`. The standalone agent is written to `jbytemod-agent/target/jbytemod-agent-<version>.jar`, and the plugin API is written to `jbytemod-api/target/jbytemod-api-<version>.jar` for third-party plugin projects.


### Getting Started

-   **Opening Files**: Use the drag and drop feature or navigate through `File` > `Open` to load `.jar`, `.apk`, `.aab`, or `.class` files.
-   **Editing Resources**: Select a text file under `Resources` to edit it with syntax highlighting, then use `Ctrl+S` or `Save resource` to apply it to the open archive.
-   **Editing Bytecode**: Select a method from the left panel to view and modify its bytecode.
-   **Decompiling**: Switch to the `Decompiler` tab to view and edit decompiled Java source code.
-   **Generating Control Flow Diagrams**: In the `Analysis` tab, select a method to generate and view its control flow diagram, you can also save it by clicking `Save`.
-   **Saving Changes**: After making edits, save your changes via `File` > `Save`. APK and AAB classes are compiled back to DEX and written with the original resources and module paths, then JByteMod prompts for a debug or custom signing key before signing and verifying the archive. APK output is also zip-aligned.

### Contributing

Contributions to JByteMod Remastered are encouraged! Follow these steps to contribute:

1.  Fork the repository.
2.  Create a new branch (`git checkout -b feature/your-feature`).
3.  Make your changes and commit them (`git commit -am 'Add some feature'`).
4.  Push to the branch (`git push origin feature/your-feature`).
5.  Create a new Pull Request.

### Issues

Report any bugs or suggest improvements on the [issue tracker](https://github.com/jbytemod/JByteMod-Remastered/issues).

## License

JByteMod Remastered is a modified work based on JByteMod and is distributed under the [GNU General Public License version 2 only (`GPL-2.0-only`)](LICENSE). It comes with absolutely no warranty. See [NOTICE](NOTICE) for its lineage and modification notice.

Third-party components remain subject to their respective licenses. Their notices are collected in the application's license information.

## Acknowledgements

-   [JByteMod](https://github.com/loerting/JByteMod-Beta), originally developed by loerting.
-   [JByteMod Reborn](https://github.com/DotRacel/JByteMod-Reborn), maintained by Panda.
-   JByteMod Remastered, maintained by xBrownieCodez and its contributors.
-   All contributors and community members who continue to support the project.

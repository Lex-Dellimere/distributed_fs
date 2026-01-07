# FreeDRS (Distributed Resource System) - Java


A lightweight Distributed File System (DFS) implementation featuring a Java-based backend and a Python/Tkinter management console. This system allows for basic file operations (upload, download, list, delete) across a network using a client-server architecture.

### Features

*   **Virtual File System (VFS)**: Manages files in a dedicated root directory (`dfs_root`) with metadata tracking.
*   **Multi-Client Support**: Handles concurrent connections from multiple clients using a thread pool.
*   **Centralized Management**: Use `program.py` to launch the server and multiple client instances from a single GUI, currently used for testing.
*   **Automated Setup**: Scripts for installation, cleanup, and removal.

### Scripts Overview

*   **`installer.py`**: Automates the installation process. It checks for dependencies (JDK, Maven), builds the Java source code, and sets up the installation directory.
*   **`program.py`**: The main entry point. Provides a GUI (Tkinter) to launch and manage the DFS server and client instances.
*   **`cleanup.py`**: Removes build artifacts, temporary files, and server data to reset the environment.
*   **`remove.py`**: Uninstalls the DFSJ application folder.

### Running the Project

To start the management console without keeping a terminal window open:

*   **Run** `python installer.py` Pick a location you want the project to be in.
* **Run** `python program.py` To launch the gui of the application
* **Run** `python cleanup.py` To remove the virtual file system and start fresh
* **Run** `python remove.py` To remove location in which the project is installed at.
---

### Installed Structure
* **dist** This directory hosts the `client.jar` and `server.jar` files.
* **dfs_root** This is the virtual file system and will contain a `server_metadata.json` file.
---
### NOTES

* Sometimes there is an issue where the client, server jar files run in the bg. I think it is fixed.
<img width="1717" height="1391" alt="client" src="https://github.com/user-attachments/assets/b6ed45d9-84b7-46aa-b0c5-0b3bdeaaaf04" />
<img width="1037" height="707" alt="config" src="https://github.com/user-attachments/assets/22cb131b-4cfe-40b7-8f80-588bbc7df972" />
<img width="3411" height="833" alt="resource" src="https://github.com/user-attachments/assets/bc365cd4-6b7d-426e-bb8f-0b69a217b154" />
<img width="3288" height="1041" alt="servlog" src="https://github.com/user-attachments/assets/9411462c-6218-4102-9acb-29f60d600b0d" />

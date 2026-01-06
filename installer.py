import subprocess
import sys
import os
import shutil
import platform

def is_tool_installed(name):
    return shutil.which(name) is not None

def install_jdk_linux():
    print("Attempting to install OpenJDK 25 on Linux...")
    try:


        subprocess.check_call(["sudo", "apt-get", "update"])
        subprocess.check_call(["sudo", "apt-get", "install", "-y", "openjdk-21-jdk"])
        print("Installed JDK 21 (LTS) as a fallback.")
    except Exception as e:
        print(f"Failed to install JDK: {e}")

def install_maven_linux():
    print("Attempting to install Maven on Linux...")
    try:
        subprocess.check_call(["sudo", "apt-get", "install", "-y", "maven"])
    except Exception as e:
        print(f"Failed to install Maven: {e}")

def build_projects():
    print("Building projects...")
    try:

        if not os.path.exists("server/pom.xml") or not os.path.exists("client/pom.xml"):
            print("Error: Could not find project source directories. Please run from project root.")
            return False

        subprocess.check_call(["mvn", "clean", "package", "-DskipTests"], cwd="server")
        subprocess.check_call(["mvn", "clean", "package", "-DskipTests"], cwd="client")

        os.makedirs("dist", exist_ok=True)
        shutil.copy("server/target/server-1.0-SNAPSHOT.jar", "dist/server.jar")
        shutil.copy("client/target/client-1.0-SNAPSHOT.jar", "dist/client.jar")

        print("Build successful. Jars are in 'dist/'")
        return True
    except subprocess.CalledProcessError as e:
        print(f"Build failed: {e}")
        return False

def run_installer():
    print("--- DFS Project Installer ---")

    os_name = platform.system()

    if os_name != "Linux":
        print(f"Automatic installation not fully supported on {os_name} yet. Please install JDK 25 and Maven manually.")


    if not is_tool_installed("java"):
        choice = input("Java (JDK) is not found. Would you like to try installing it? (y/n): ")
        if choice.lower() == 'y' and os_name == "Linux":
            install_jdk_linux()
    else:
        print("Java is already installed.")


    if not is_tool_installed("mvn"):
        choice = input("Maven is not found. Would you like to try installing it? (y/n): ")
        if choice.lower() == 'y' and os_name == "Linux":
            install_maven_linux()
    else:
        print("Maven is already installed.")

    if not is_tool_installed("java") or not is_tool_installed("mvn"):
        print("Some dependencies are still missing. Please install them manually.")
        return

    print("All dependencies are met.")


    default_install_dir = os.path.join(os.getcwd(), "DFSJ_INSTALL")
    install_path = input(f"Enter the directory to install DFSJ (default: {default_install_dir}): ").strip()
    if not install_path:
        install_path = default_install_dir

    dfsj_dir = os.path.join(install_path, "DFSJ")

    if os.path.exists(dfsj_dir):
        confirm = input(f"Directory {dfsj_dir} already exists. Overwrite? (y/n): ")
        if confirm.lower() != 'y':
            print("Installation cancelled.")
            return
        shutil.rmtree(dfsj_dir)


    if not build_projects():
        print("Build failed. Cannot proceed with installation.")
        return


    print(f"Creating DFSJ folder at {dfsj_dir}...")
    os.makedirs(dfsj_dir)
    os.makedirs(os.path.join(dfsj_dir, "dist"))
    os.makedirs(os.path.join(dfsj_dir, "dfs_root"))


    shutil.copy("dist/server.jar", os.path.join(dfsj_dir, "dist/server.jar"))
    shutil.copy("dist/client.jar", os.path.join(dfsj_dir, "dist/client.jar"))


    scripts = ["program.py", "cleanup.py", "remove.py", "installer.py"]
    for script in scripts:
        if os.path.exists(script):
            shutil.copy(script, os.path.join(dfsj_dir, script))
        else:
            print(f"Warning: Script {script} not found, skipping.")

    if os.path.exists("dist"):
        print("Cleaning up temporary artifacts...")
        shutil.rmtree("dist")

    print(f"\nInstallation finished successfully in {dfsj_dir}")
    print(f"To run the program, go to {dfsj_dir} and run: python3 program.py")

if __name__ == "__main__":
    run_installer()

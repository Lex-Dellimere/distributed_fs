import os
import shutil
import sys

def cleanup():
    print("--- DFS Project Cleanup ---")


    directories_to_remove = []


    if os.path.exists("server/pom.xml") or os.path.exists("client/pom.xml"):
        directories_to_remove.extend([
            "dist",
            "client/target",
            "server/target"
        ])


    directories_to_remove.append("dfs_root")


    for root, dirs, files in os.walk("."):
        if "__pycache__" in dirs:
            directories_to_remove.append(os.path.join(root, "__pycache__"))

    cleaned_count = 0
    for directory in directories_to_remove:
        if os.path.exists(directory):
            print(f"Removing {directory}...")
            try:
                shutil.rmtree(directory)
                cleaned_count += 1
            except Exception as e:
                print(f"Failed to remove {directory}: {e}")


    files_to_remove = [
        "dfs_root/server_metadata.json"
    ]

    for file in files_to_remove:
        if os.path.exists(file):
            print(f"Removing {file}...")
            try:
                os.remove(file)
                cleaned_count += 1
            except Exception as e:
                print(f"Failed to remove {file}: {e}")

    if cleaned_count == 0:
        print("Nothing to clean.")
    else:
        print(f"Cleanup finished. {cleaned_count} items removed.")

if __name__ == "__main__":

    if "-y" not in sys.argv:
        confirm = input("This will delete build artifacts and server data (dfs_root). Are you sure? (y/n): ")
        if confirm.lower() != 'y':
            print("Cleanup cancelled.")
            sys.exit(0)

    cleanup()

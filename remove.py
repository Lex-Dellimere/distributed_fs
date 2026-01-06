import os
import shutil
import sys

def remove_dfsj():

    current_dir = os.path.dirname(os.path.abspath(__file__))
    dir_name = os.path.basename(current_dir)

    if dir_name == "DFSJ":
        target_dir = current_dir
        print(f"Detected DFSJ folder at: {target_dir}")
    else:

        target_dir = os.path.join(current_dir, "DFSJ")
        if not os.path.exists(target_dir):
            print("DFSJ folder not found.")
            return

    confirm = input(f"Are you sure you want to remove the DFSJ installation at {target_dir}? (y/n): ")
    if confirm.lower() != 'y':
        print("Removal cancelled.")
        return
    try:
        if os.path.abspath(os.getcwd()) == os.path.abspath(target_dir):
            os.chdir(os.path.dirname(target_dir))
            print("Changed directory to parent before removal.")

        shutil.rmtree(target_dir)
        print("DFSJ removed successfully.")
    except Exception as e:
        print(f"Error removing DFSJ: {e}")
        print("You might need to manually delete the folder if it's currently in use.")

if __name__ == "__main__":
    remove_dfsj()

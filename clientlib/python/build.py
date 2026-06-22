import shutil
import os
import subprocess

def build():
    # Source and destination paths
    root_dir = os.path.dirname(os.path.abspath(__file__))
    src_dir = os.path.abspath(os.path.join(root_dir, "../../gencode/python/udmi/schema"))
    dest_dir = os.path.abspath(os.path.join(root_dir, "src/udmi/schema"))

    print(f"Temporarily replacing symlink with physical files from {src_dir}...")
    
    # Check if dest is a symlink and remove it
    was_symlink = False
    if os.path.islink(dest_dir):
        was_symlink = True
        os.unlink(dest_dir)
            
    # Copy the files
    if os.path.exists(src_dir):
        shutil.copytree(src_dir, dest_dir)
    else:
        print(f"Error: Source directory {src_dir} does not exist. Cannot build.")
        return

    try:
        # Run poetry build
        print("Running poetry build...")
        subprocess.run(["poetry", "build"], check=True)
    finally:
        # Clean up and restore symlink
        print("Cleaning up and restoring symlink...")
        shutil.rmtree(dest_dir)
        if was_symlink:
            # Recreate the symlink
            os.symlink("../../../../gencode/python/udmi/schema/", dest_dir)

if __name__ == "__main__":
    build()

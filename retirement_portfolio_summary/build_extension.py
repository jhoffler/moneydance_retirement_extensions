import os
import urllib.request
import zipfile
import subprocess
import shutil

# 1. Paths
base_dir = os.path.dirname(os.path.abspath(__file__))
devkit_lib = r"C:\Program Files\Moneydance\lib\moneydance.jar"
kotlin_compiler_dir = os.path.join(base_dir, "kotlin-compiler")
kotlinc_path = os.path.join(kotlin_compiler_dir, "kotlinc", "bin", "kotlinc.bat")
build_dir = os.path.join(base_dir, "build")
mxt_file = os.path.join(base_dir, "retirement_portfolio_summary.mxt")

# 2. Download Kotlinc if not present
if not os.path.exists(kotlinc_path):
    print("Kotlin compiler not found. Downloading...")
    url = "https://github.com/JetBrains/kotlin/releases/download/v1.9.23/kotlin-compiler-1.9.23.zip"
    zip_path = os.path.join(base_dir, "kotlin-compiler.zip")
    
    # Download with progress
    def progress(block_num, block_size, total_size):
        read_so_far = block_num * block_size
        if total_size > 0:
            percent = read_so_far * 100 / total_size
            print(f"Downloaded {percent:.1f}%", end="\r")
            
    urllib.request.urlretrieve(url, zip_path, progress)
    print("\nExtracting Kotlin compiler...")
    
    with zipfile.ZipFile(zip_path, 'r') as zip_ref:
        zip_ref.extractall(kotlin_compiler_dir)
        
    os.remove(zip_path)
    print("Kotlin compiler installed.")

# 3. Clean and prepare build dir
if os.path.exists(build_dir):
    shutil.rmtree(build_dir)
os.makedirs(build_dir)

# 4. Compile Kotlin code
print("Compiling Kotlin extension...")
source_file = os.path.join(base_dir, "com", "moneydance", "modules", "features", "retirement_portfolio_summary", "Main.kt")
cmd = [
    kotlinc_path,
    source_file,
    "-classpath", devkit_lib,
    "-d", build_dir,
    "-jvm-target", "17"
]

# Point to the JDK JRE we found in the IDE java extension
java_home = r"C:\Users\jhoff\.antigravity-ide\extensions\redhat.java-1.55.0-win32-x64\jre\21.0.11-win32-x86_64"
env = os.environ.copy()
env["JAVA_HOME"] = java_home

result = subprocess.run(cmd, env=env, capture_output=True, text=True)
if result.returncode != 0:
    print("Compilation failed!")
    print(result.stdout)
    print(result.stderr)
    exit(1)
else:
    print("Compilation successful.")

# 5. Package into MXT
print("Packaging MXT file...")
meta_info_src = os.path.join(base_dir, "com", "moneydance", "modules", "features", "retirement_portfolio_summary", "meta_info.dict")

with zipfile.ZipFile(mxt_file, 'w') as zipf:
    # Add compiled classes
    for root, dirs, files in os.walk(build_dir):
        for file in files:
            file_path = os.path.join(root, file)
            # Compute arcname relative to build_dir
            arcname = os.path.relpath(file_path, build_dir)
            zipf.write(file_path, arcname)
            
    # Add meta_info.dict in the correct path
    zipf.write(meta_info_src, "com/moneydance/modules/features/retirement_portfolio_summary/meta_info.dict")

print(f"Successfully created: {mxt_file}")

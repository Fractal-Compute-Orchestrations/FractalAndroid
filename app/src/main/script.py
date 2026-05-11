import os

IGNORED_DIRS = {".git", "node_modules", "__pycache__", "venv", "build", "dist"}

def is_text_file(filepath, blocksize=512):
    """
    Heuristic check: try reading a small chunk and see if it looks binary.
    """
    try:
        with open(filepath, 'rb') as f:
            chunk = f.read(blocksize)
            if b'\x00' in chunk:
                return False
        return True
    except Exception:
        return False


def count_lines(filepath):
    count = 0
    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
            for _ in f:
                count += 1
    except Exception:
        pass
    return count


def count_directory(root_dir):
    total_lines = 0
    total_files = 0

    for root, dirs, files in os.walk(root_dir):
        dirs[:] = [d for d in dirs if d not in IGNORED_DIRS]

        for file in files:
            filepath = os.path.join(root, file)

            if is_text_file(filepath):
                lines = count_lines(filepath)
                total_lines += lines
                total_files += 1

    return total_files, total_lines


if __name__ == "__main__":
    files, lines = count_directory(".")

    print(f"Total text files: {files}")
    print(f"Total lines: {lines}")
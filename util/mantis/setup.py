from setuptools import setup
import os

packages = ["mantis"]
for root, dirs, _ in os.walk("v2"):
    for d in dirs:
        if d not in ("__pycache__", ".git"):
            rel = os.path.relpath(os.path.join(root, d), "v2").replace(os.path.sep, ".")
            packages.append(f"mantis.{rel}")

setup(
    name="mantis",
    version="2.0.0",
    package_dir={"mantis": "v2"},
    packages=packages,
    package_data={
        "mantis.engine.config": ["*.yaml"],
    },
    install_requires=[
        "google-genai>=2.7.0",
        "PyYAML>=6.0.2",
        "pydantic>=2.0.0",
    ],
    entry_points={
        "console_scripts": [
            "mantis=mantis.cli:main",
        ],
    },
    classifiers=[
        "Programming Language :: Python :: 3",
        "Operating System :: OS Independent",
    ],
    python_requires=">=3.10",
)

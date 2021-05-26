from setuptools import setup, find_packages
import shutil
import os

def iter_schema_files():
    schema_dir = os.path.join("..", "..", "schema")
    dst_dir = os.path.join("src", "udmi", "schema")
    if os.path.exists(dst_dir):
        shutil.rmtree(dst_dir)
    os.makedirs(dst_dir)

    for filename in os.listdir(schema_dir):
        if filename.endswith("json"):
            src = os.path.join(schema_dir, filename)
            dst = os.path.join(dst_dir, filename)
            shutil.copy(src, dst)
            module_filepath = os.path.join("schema", filename)
            yield(module_filepath)

setup(
    name='pyudmi',
    version='0.0.5',
    url='https://github.com/faucetsdn/udmi/contrib/pyudmi',
    author='Paul Harter',
    author_email='paul@glowinthedark.co.uk',
    license="LICENSE",
    description='Helper classes for working with udmi',
    packages=find_packages('src'),
    package_data={"udmi": [f for f in iter_schema_files()]},
    package_dir={'udmi': 'src/udmi'},
    install_requires=['pytz', 'fastjsonschema', 'python-dateutil']
)
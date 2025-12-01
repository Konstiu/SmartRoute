@echo off
cd /d %~dp0

echo Setting up Python 3.12 virtual environment...

python3.12 -m venv .venv --copies

call .venv\Scripts\activate

pip install --upgrade pip

pip install -r requirements.txt

echo Python environment setup complete!
echo To activate: python\.venv\Scripts\activate

# From the python/ directory
python3.12 -m venv .venv --copies

# Activate it
source .venv/bin/activate # On Windows: .venv\Scripts\activate

# Verify Python version
python --version # Should show Python 3.12.x

# Install dependencies
pip install --upgrade pip
pip install -r requirements.txt

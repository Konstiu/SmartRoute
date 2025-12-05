# Garmin Connect Python Script

This directory contains a small Python script used to interact with the Garmin Connect API.

## 📦 Requirements

* **Python 3.12 is required**
* Recommended: a virtual environment (created automatically by `setup.sh` / `setup.bat`)

Install dependencies **in one of two ways**:

### Option A — Use the setup script (recommended)

This will create a `.venv`, install all dependencies and ensure you run the script with **Python 3.12 inside the virtual environment**

#### Linux / macOS

```bash
./setup.sh
```

#### Windows

```bat
setup.bat
```

### Option B — Install manually

If you skip the setup script, you must install dependencies yourself:

```bash
pip install -r requirements.txt
```

In this case, you run the script with your system’s `python` (must be Python 3.12).

---

## Usage

### If you used `setup.sh` / `setup.bat`

Run using the **Python inside the `.venv`**:

```bash
.venv/bin/python3.12 python_garmin_connect.py \
    YOUR_GARMIN_EMAIL \
    YOUR_GARMIN_PASSWORD \
    10
```

### If you installed manually

Run using your system Python (must be Python 3.12):

```bash
python python_garmin_connect.py \
    YOUR_GARMIN_EMAIL \
    YOUR_GARMIN_PASSWORD \
    10
```

### Positional arguments

| Position | Name       | Description                   | Required |
|---------:|------------|-------------------------------|----------|
|        1 | `email`    | Garmin account email          | No       |
|        2 | `password` | Garmin account password       | No       |
|        3 | `count`    | Number of activities to fetch | Yes      |

If you don't want to use your email and password, you can alternatively use your token, which is being returned upon a successful request. with ```.venv/bin/python3.12 python_garmin_connect.py --token-json "$TOKEN_JSON" 3 ```
For Windows support you can execute the programm with the flag --token-bas64, ```.venv/bin/python3.12 python_garmin_connect.py --token-base64 "$BASE_64" 3 ``` since windows has some issues with escaping the ```"``` you have to encode your token with base64

---

## Virtual Environment

If using the setup script, the `.venv` environment is created automatically.

To activate manually:

### Linux / macOS

```bash
source .venv/bin/activate
```

### Windows

```bat
.venv\Scripts\activate
```

---

## 📁 Project Structure

```
python/
├── python_garmin_connect.py
├── requirements.txt
├── setup.sh
├── setup.bat
└── .venv/
```

---
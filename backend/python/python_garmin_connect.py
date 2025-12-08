#!/usr/bin/env python3.12
import json
import sys
import os
import tempfile
from pathlib import Path
from garminconnect import Garmin
import base64
import traceback


def collect_last_runs(api: Garmin, target_count: int):
    """Fetch recent activities and return the last `target_count` running activities."""
    runs = []
    start = 0
    page_size = 100

    while len(runs) < target_count:
        activities = api.get_activities(start, page_size)
        if not activities:
            break

        for act in activities:
            if act.get("sportTypeId") == 1:  # Running activities
                runs.append(act)
                if len(runs) == target_count:
                    break
        start += page_size

    return runs[:target_count]


def fetch_details_for_ids(api: Garmin, activities):
    """For each activity, fetch detailed information."""
    results = []
    for act in activities:
        activity_id = act["activityId"]
        details = api.get_activity_details(activity_id)

        results.append({
            "activityId": activity_id,
            "activityName": act.get("activityName"),
            "startTimeLocal": act.get("startTimeLocal"),
            "summary": act,
            "details": details,
        })

    return results


def to_garth_b64(obj):
    """Normalize several common token input shapes into the base64
    string expected by `garth.Client.loads()`.

    Accepts:
      - a base64 string produced by `garth.dumps()` (returned as-is)
      - a JSON string representing a list or dict
      - a parsed dict/list (from json.loads)
    """
    # If it's a string, first try to detect if it's already base64
    if isinstance(obj, str):
        # try to decode as base64 JSON
        try:
            dec = base64.b64decode(obj)
            json.loads(dec)
            return obj
        except Exception:
            # not valid base64 payload -> try parsing as JSON string
            try:
                parsed = json.loads(obj)
            except Exception:
                raise ValueError("Token input is neither valid base64 nor JSON string")
            obj = parsed

    # Now obj is a Python object (list or dict)
    if isinstance(obj, list) and len(obj) == 2:
        arr = obj
    elif isinstance(obj, dict):
        # handle common dict shapes
        if 'oauth1_token.json' in obj and 'oauth2_token.json' in obj:
            arr = [obj['oauth1_token.json'], obj['oauth2_token.json']]
        elif 'oauth1' in obj and 'oauth2' in obj:
            arr = [obj['oauth1'], obj['oauth2']]
        else:
            # fallback: try to extract first two dict values
            vals = [v for v in obj.values() if isinstance(v, dict)]
            if len(vals) >= 2:
                arr = vals[:2]
            else:
                raise ValueError("Unsupported token JSON format for garth.loads")
    else:
        raise ValueError("Unsupported token format for garth.loads")

    return base64.b64encode(json.dumps(arr).encode()).decode()


def main():
    # Support three invocation patterns:
    # 1) Legacy credential mode: script.py <email> <password> <activity_count>
    # 2) Inline token JSON: script.py --token-json '<json>' <activity_count>
    # 3) Inline base64 encoded token script.py --token-base64 '<base63>' <activity_count>

    if len(sys.argv) < 2:
        print(json.dumps({
            "error": "Invalid arguments. Usage:\n\n" +
                     "  python script.py user@example.com password123 10\n" +
                     "  python script.py --token-json '{\"token\":\"...\"}' 10\n" +
                     "  python script.py --token-base64 'base64string' 10"
        }),              file=sys.stderr)
        sys.exit(1)

    tokenstore_temp = None
    tokenstore_path = None
    email = None
    password = None
    target_count = None

    inline_obj = None
    if sys.argv[1] == "--token-json":
        if len(sys.argv) < 3:
            print(json.dumps({"error": "Missing token JSON"}), file=sys.stderr)
            sys.exit(1)
        if len(sys.argv) < 4:
            print(json.dumps({"error": "Missing activity_count"}), file=sys.stderr)
            sys.exit(1)
        inline = sys.argv[2]
        try:
            obj = json.loads(inline)
        except Exception as e:
            print(json.dumps({"error": f"Invalid token JSON: {e}"}), file=sys.stderr)
            sys.exit(1)
        try:
            target_count = int(sys.argv[3])
        except Exception:
            print(json.dumps({"error": "activity_count must be integer"}), file=sys.stderr)
            sys.exit(1)

        # Keep inline object in memory; we'll try in-memory injection first and
        # only write temporary token files as a fallback if injection fails.
        inline_obj = obj

    elif sys.argv[1] == "--token-base64":
        if len(sys.argv) < 3:
            print(json.dumps({"error": "Missing token JSON"}), file=sys.stderr)
            sys.exit(1)
        if len(sys.argv) < 4:
            print(json.dumps({"error": "Missing activity_count"}), file=sys.stderr)
            sys.exit(1)
        inline = sys.argv[2]
        try:
            inline = base64.b64decode(inline).decode('utf-8')
            obj = json.loads(inline)
        except Exception as e:
            print(json.dumps({"error": f"Invalid token JSON: {e}"}), file=sys.stderr)
            sys.exit(1)
        try:
            target_count = int(sys.argv[3])
        except Exception:
            print(json.dumps({"error": "activity_count must be integer"}), file=sys.stderr)
            sys.exit(1)

        inline_obj = obj

    elif len(sys.argv) == 4 and '@' in sys.argv[1]:
        # legacy credential invocation
        email = sys.argv[1]
        password = sys.argv[2]
        try:
            target_count = int(sys.argv[3])
        except Exception:
            print(json.dumps({"error": "activity_count must be integer"}), file=sys.stderr)
            sys.exit(1)

    else:
        print(json.dumps({"error": "Unrecognized invocation pattern"}), file=sys.stderr)
        sys.exit(1)

    try:
        # If inline_obj is present, try to inject tokens in-memory into garth
        api = None
        if inline_obj is not None:
            try:
                api = Garmin()
                try:
                    b64 = to_garth_b64(inline_obj)
                    api.garth.loads(b64)
                    # test that the injected tokens work by attempting a lightweight call
                    try:
                        _test = api.get_activities(0, 1)
                    except Exception:
                        # injection didn't work; fall back to file-based approach
                        api = None
                except Exception as e:
                    print(f"Debug: in-memory injection failed: {e}", file=sys.stderr)
                    api = None
            except Exception as e:
                print(e, file=sys.stderr)
                print("Debug: In-memory token injection failed, falling back to file-based", file=sys.stderr)
                api = None

        # If we didn't get an API client via in-memory injection, use credentials
        if api is None:
            api = Garmin(email, password)
            api.login()

        # Collect runs
        runs = collect_last_runs(api, target_count)

        if not runs:
            print(json.dumps({"error": "No runs found"}), file=sys.stderr)
            if tokenstore_temp:
                tokenstore_temp.cleanup()
            sys.exit(1)

        # Fetch details
        runs_with_details = fetch_details_for_ids(api, runs)

        # Collect token info to include in output (without persistently saving)
        tokens = {}
        try:
            if tokenstore_path is not None and tokenstore_path.exists():
                for f in tokenstore_path.glob("*.json"):
                    try:
                        tokens[f.name] = json.loads(f.read_text())
                    except Exception:
                        tokens[f.name] = f.read_text()
        except Exception:
            pass

        # Try to extract in-memory tokens from api.garth if available
        try:
            if hasattr(api, 'garth'):
                garth = api.garth
                for attr in ('tokens', 'token', 'token_json', 'session', 'oauth', 'cookiejar'):
                    try:
                        val = getattr(garth, attr)
                        if val is None:
                            continue
                        try:
                            json.dumps(val)
                            tokens[f'in_memory_{attr}'] = val
                        except Exception:
                            tokens[f'in_memory_{attr}'] = repr(val)
                    except Exception:
                        continue
        except Exception:
            pass

        # If we still don't have token files, try to force a dump from garth into a temporary dir
        # This is what the upstream example does: garth.dump(path) writes token files.
        if not tokens:
            try:
                if hasattr(api, 'garth') and hasattr(api.garth, 'dump'):
                    _td = tempfile.TemporaryDirectory()
                    try:
                        api.garth.dump(_td.name)
                        _p = Path(_td.name)
                        for f in _p.glob('*.json'):
                            try:
                                tokens[f.name] = json.loads(f.read_text())
                            except Exception:
                                tokens[f.name] = f.read_text()
                    finally:
                        try:
                            _td.cleanup()
                        except Exception:
                            pass
            except Exception:
                # best-effort only; don't fail the whole script if dump isn't supported
                pass

        result = {"tokens": tokens, "activities": runs_with_details}

        # Output JSON to stdout
        print(json.dumps(result, ensure_ascii=False))

        if tokenstore_temp:
            tokenstore_temp.cleanup()

    except Exception as e:
        error_info = {
            "error": str(e),
            "type": type(e).__name__,
            "traceback": traceback.format_exc()
        }
        print(json.dumps(error_info), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3.12
import json
import sys
import os
import tempfile
from pathlib import Path
from garminconnect import Garmin  # you can actually remove this import now
import base64
import traceback

MOCK_ACTIVITY = {
    "activityId": 21013233687,
    "activityName": "Vienna Running",
    "startTimeLocal": "2025-11-17 12:02:52",
    "startTimeGMT": "2025-11-17 11:02:52",
    "activityType": {
        "typeId": 1,
        "typeKey": "running",
        "parentTypeId": 17,
        "isHidden": False,
        "restricted": False,
        "trimmable": True
    },
    "eventType": {
        "typeId": 9,
        "typeKey": "uncategorized",
        "sortOrder": 10
    },
    "distance": 3610.219970703125,
    "duration": 1554.64599609375,
    "elapsedDuration": 1554.64599609375,
    "movingDuration": 1395.0,
    "elevationGain": 31.0,
    "elevationLoss": 28.0,
    "averageSpeed": 2.322000026702881,
    "maxSpeed": 4.031000137329102,
    "startLatitude": 48.19979136809707,
    "startLongitude": 16.36750769801438,
    "hasPolyline": True,
    "hasImages": False,
    "ownerId": 128143580,
    "ownerDisplayName": "85ff30d7-205d-4b76-b4bf-1ae4fda606a6",
    "ownerFullName": "Konsti",
    "ownerProfileImageUrlSmall": "https://s3.amazonaws.com/garmin-connect-prod/profile_images/bb036c9e-1055-4a58-8a6c-f9c5694b6509-prth.png",
    "ownerProfileImageUrlMedium": "https://s3.amazonaws.com/garmin-connect-prod/profile_images/bb036c9e-1055-4a58-8a6c-f9c5694b6509-prfr.png",
    "ownerProfileImageUrlLarge": "https://s3.amazonaws.com/garmin-connect-prod/profile_images/bb036c9e-1055-4a58-8a6c-f9c5694b6509-prof.png",
    "calories": 366.0,
    "bmrCalories": 41.0,
    "averageHR": 164.0,
    "maxHR": 185.0,
    "averageRunningCadenceInStepsPerMinute": 130.5625,
    "maxRunningCadenceInStepsPerMinute": 250.0,
    "steps": 3576,
    "privacy": {"typeId": 2, "typeKey": "private"},
    "userPro": False,
    "hasVideo": False,
    "timeZoneId": 124,
    "beginTimestamp": 1763377372000,
    "sportTypeId": 1,
    "avgPower": 279.0,
    "maxPower": 514.0,
    "aerobicTrainingEffect": 3.0,
    "anaerobicTrainingEffect": 0.3,
}

MOCK_DETAILS = {
    "activityId": 21013233687,
    "measurementCount": 24,
    "metricsCount": 1556,
    "totalMetricsCount": 1556,
    "metricDescriptors": [
        {"metricsIndex": 0, "key": "directElevation", "unit": {"id": 1, "key": "meter", "factor": 100.0}},
        {"metricsIndex": 1, "key": "sumMovingDuration", "unit": {"id": 40, "key": "second", "factor": 1000.0}},
        {"metricsIndex": 2, "key": "directGradeAdjustedSpeed", "unit": {"id": 20, "key": "mps", "factor": 0.1}},
        {"metricsIndex": 15, "key": "directLongitude", "unit": {"id": 60, "key": "dd", "factor": 1.0}},
        {"metricsIndex": 3, "key": "directLatitude", "unit": {"id": 60, "key": "dd", "factor": 1.0}},
        {"metricsIndex": 10, "key": "directTimestamp", "unit": {"id": 120, "key": "gmt", "factor": 0.0}},
    ],
    "activityDetailMetrics": [
        {"metrics": [0.0, 0.0, 27.0, 48.185244826599956, 0.0, 0.0, 0.0, 79.0, 0.0, 103.0, 1762772749000.0, 0.0, 193.0, 0.0, 0.0, 16.35513617657125, 0.0, None, None, None, None, None, None, None]},
        {"metrics": [0.0, 1.0, 27.0, 48.185262428596616, 0.0, 0.0, 0.0, 79.0, 0.0, 102.0, 1762772750000.0, 0.0, 193.0, 1.0, 1.2400000095367432, 16.355094518512487, 0.0, 0.0, None, None, None, None, None, None]},
        {"metrics": [0.0, 2.0, 27.0, 48.185277599841356, 0.0, 0.0, 0.0, 79.0, 0.0, 102.0, 1762772751000.0, 0.0, 193.0, 2.0, 3.4200000762939453, 16.35511036030948, 0.0, 0.0, None, None, None, None, None, None]},
        {"metrics": [0.0, 3.0, 27.0, 48.185283467173576, 0.0, 0.0, 0.0, 79.0, 0.0, 102.0, 1762772752000.0, 0.0, 192.8000030517578, 3.0, 5.559999942779541, 16.355140283703804, 0.0, -0.20000000298023224, None, None, None, None, None, None]},
        {"metrics": [0.0, 4.0, 27.0, 48.185293693095446, 1.4839999675750732, 0.0, 1.4839999675750732, 79.0, 0.0, 102.0, 1762772753000.0, 1.0, 192.8000030517578, 4.0, 7.949999809265137, 16.355173476040363, 0.0, 0.0, None, None, None, None, None, None]},
        {"metrics": [0.0, 5.0, 27.0, 48.18529008887708, 2.135999917984009, 0.0, 2.0429999828338623, 79.0, 0.0, 102.0, 1762772754000.0, 2.0, 192.8000030517578, 5.0, 10.680000305175781, 16.355218067765236, 0.0, 0.0, None, None, None, None, None, None]},
        {"metrics": [0.0, 6.0, 27.0, 48.185296123847365, 2.447999954223633, 0.0, 2.3420000076293945, 79.0, 0.0, 102.0, 1762772755000.0, 3.0, 192.8000030517578, 6.0, 13.640000343322754, 16.355262575671077, 0.0, 0.0, None, None, None, None, None, None]},
        {"metrics": [329.0, 7.0, 27.0, 48.18528505973518, 2.75, 158.0, 2.63100004196167, 79.0, 0.0, 102.0, 1762772756000.0, 4.0, 192.8000030517578, 7.0, 16.6200008392334, 16.355326026678085, 79.0, 0.0, 99.9, 294.0, 9.050000190734863, 329.0, 9.040000152587892, None]}
    ]
}


def collect_last_runs(api, target_count: int):
    """Mock: just return target_count copies of MOCK_ACTIVITY."""
    return [MOCK_ACTIVITY for _ in range(target_count)]


def fetch_details_for_ids(api, activities):
    """Mock: wrap each activity with MOCK_DETAILS."""
    return [
        {
            "activityId": act["activityId"],
            "activityName": act["activityName"],
            "startTimeLocal": act["startTimeLocal"],
            "summary": act,
            "details": MOCK_DETAILS,
        }
        for act in activities
    ]


def main():
    # Same CLI contract as the real script, just no real Garmin usage.
    if len(sys.argv) < 2:
        print(json.dumps({
            "error": "Invalid arguments. Usage:\n\n" +
                     "  python script.py user@example.com password123 10\n" +
                     "  python script.py --token-json '{\"token\":\"...\"}' 10\n" +
                     "  python script.py --token-base64 'base64string' 10"
        }),              file=sys.stderr)
        sys.exit(1)

    target_count = None

    if sys.argv[1] == "--token-json":
        if len(sys.argv) < 4:
            print(json.dumps({"error": "Missing token JSON or activity_count"}), file=sys.stderr)
            sys.exit(1)
        # we don't actually need the token JSON in mock mode, just validate it's at least parseable
        try:
            json.loads(sys.argv[2])
        except Exception as e:
            print(json.dumps({"error": f"Invalid token JSON: {e}"}), file=sys.stderr)
            sys.exit(1)
        try:
            target_count = int(sys.argv[3])
        except Exception:
            print(json.dumps({"error": "activity_count must be integer"}), file=sys.stderr)
            sys.exit(1)

    elif sys.argv[1] == "--token-base64":
        if len(sys.argv) < 3:
            print(json.dumps({"error": "Missing token JSON"}), file=sys.stderr)
            sys.exit(1)
        if len(sys.argv) < 4:
            print(json.dumps({"error": "Missing activity_count"}), file=sys.stderr)
            sys.exit(1)
        inline = sys.argv[2]
        try:
            inline_obj = base64.b64decode(inline_obj).decode('utf-8')
            obj = json.loads(inline)
        except Exception as e:
            print(json.dumps({"error": f"Invalid token JSON: {e}"}), file=sys.stderr)
            sys.exit(1)
        try:
            target_count = int(sys.argv[3])
        except Exception:
            print(json.dumps({"error": "activity_count must be integer"}), file=sys.stderr)
            sys.exit(1)

    elif len(sys.argv) == 4 and '@' in sys.argv[1]:
        # legacy email/password invocation
        try:
            target_count = int(sys.argv[3])
        except Exception:
            print(json.dumps({"error": "activity_count must be integer"}), file=sys.stderr)
            sys.exit(1)
    else:
        print(json.dumps({"error": "Unrecognized invocation pattern"}), file=sys.stderr)
        sys.exit(1)

    try:
        runs = collect_last_runs(None, target_count)
        if not runs:
            print(json.dumps({"error": "No runs found"}), file=sys.stderr)
            sys.exit(1)

        runs_with_details = fetch_details_for_ids(None, runs)

        tokens = {
            "oauth2_token.json": {
                "scope": "DUMMY_SCOPE",
                "jti": "dummy-jti",
                "token_type": "bearer",
                "access_token": "dummy-token",
                "expires_in": 99999,
                "expires_at": 1764344707,
                "refresh_token_expires_in": 2591999,
                "refresh_token_expires_at": 1766837146
            },
            "oauth1_token.json": {
                "oauth_token": "dummy-token",
                "oauth_token_secret": "dummy_auth",
                "mfa_token": None,
                "mfa_expiration_timestamp": None,
                "domain": "garmin.com"
            }
        }

        result = {"tokens": tokens, "activities": runs_with_details}
        print(json.dumps(result, ensure_ascii=False))

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

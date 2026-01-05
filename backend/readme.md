# SmartRoute Backend

---

## Table of Contents

- [Push Notifications (Mobile Apps & PWAs)](#push-notifications-mobile-apps--pwas)
  - [Overview](#overview)
  - [Web Push (PWA)](#web-push-pwa)
    - [VAPID Keys](#vapid-keys)
    - [Public VAPID Key](#public-vapid-key)
    - [Private VAPID Key (Required)](#private-vapid-key-required)
    - [VAPID Subject](#vapid-subject)
  - [Firebase Cloud Messaging (FCM)](#firebase-cloud-messaging-fcm)
  - [Security Notes](#security-notes)
  - [Minimal Required Configuration (Production)](#minimal-required-configuration-production)
- [How to connect your Strava Account Without a Frontend](#how-to-connect-your-strava-account-without-a-frontend)
- [Configuration Summary](#configuration-summary)

---

## Push Notifications (Mobile Apps & PWAs)

This backend supports **push notifications** for both **native mobile applications** and **Progressive Web Apps (PWAs)**.
Depending on the client type, different push technologies are used.

---

### Overview

| Client Type                        | Technology                         |
| ---------------------------------- | ---------------------------------- |
| Native Mobile Apps (Android / iOS) | **Firebase Cloud Messaging (FCM)** |
| Progressive Web Apps (PWA)         | **Web Push Protocol (VAPID)**      |

---

### Web Push (PWA)

PWAs use the **Web Push Protocol**, which requires **VAPID keys** for authentication.

#### VAPID Keys

VAPID consists of:

- **Public Key** → shared with the frontend
- **Private Key** → must remain secret and **never be committed**

---

#### Public VAPID Key

The public key is **hardcoded by default**, but can be overridden.

##### Default configuration

```properties
vapid.public.key=BHpFgSD4JeTk9Y5NsOBYs8hxqXBS1ocDB1CCkedh45gvRBnlaDOh9lQI8wUOEfr5olcx4m-MpnRL9T2oaTBTec4
```

##### Override options

You can override the public key in **one of the following ways**:

###### 1. `application.properties`

```properties
vapid.public.key=YOUR_PUBLIC_KEY
```

###### 2. Environment Variable

> ⚠️ Unix environment variables cannot contain dots (`.`)

Use:

```bash
export VAPID_PUBLIC_KEY=YOUR_PUBLIC_KEY
```

The application will automatically resolve:

```properties
vapid.public.key=${VAPID_PUBLIC_KEY}
```

---

#### Private VAPID Key (Required)

The **private key must always be provided by you** and must remain secret.

##### Configuration options

###### 1. `application-secrets.properties`

```properties
vapid.private.key=YOUR_PRIVATE_KEY
```

###### 2. Environment Variable (Recommended)

```bash
export VAPID_PRIVATE_KEY=YOUR_PRIVATE_KEY
```

Resolved in configuration as:

```properties
vapid.private.key=${VAPID_PRIVATE_KEY:dummyKey}
```

> ⚠️ `dummyKey` is only a fallback and **will not work in production**.

---

#### VAPID Subject

The subject identifies the sender and is required by the Web Push specification.

```properties
vapid.subject=mailto:konstantin.unterweger@gmail.com
```

This should typically be:

- a `mailto:` address **or**
- a valid URL belonging to your project

---

### Firebase Cloud Messaging (FCM)

Native mobile apps rely on **Firebase Cloud Messaging**.

#### Firebase Service Account

A Firebase **service account JSON file** is required to authenticate the backend.

##### Configuration

```properties
firebase.service.account.path=${firebase_service_account_path:file:firebase-service-account.json}
```

##### Options

- **Default**:
  Uses `firebase-service-account.json` from the project root

- **Custom path via environment variable**:

```bash
export firebase_service_account_path=/secure/path/firebase-service-account.json
```

---

### Security Notes

- **Never commit**:

  - `firebase-service-account.json`
  - VAPID private keys
- Use environment variables in:

  - production
  - CI/CD pipelines
- Store secrets in:

  - `.gitignore`-protected files
  - secret managers (recommended)

---

### Minimal Required Configuration (Production)

For production, you **must provide**:

- ✅ `VAPID_PRIVATE_KEY`
- ✅ `VAPID_PUBLIC_KEY` (if not using default)
- ✅ `firebase-service-account.json` or `firebase_service_account_path`

---

## How to connect your Strava Account Without a Frontend

Strava uses an OAuth2 Authorization Code Flow that requires browser redirects.
Because Swagger or direct API calls cannot handle this flow, you need to perform the connection manually.

---

### Requirements

Ensure the following values are correctly configured in `application-secrets.properties`:

- `strava.client.id`
- `strava.client.secret`

---

### Connect a Strava account to a user

1. Authenticate your API user
   Obtain a Bearer token (JWT) by sending:

    ```
    POST {backendBaseUrl}/api/v1/authentication
    ```

   with a valid request body.

   Use any tool like **Postman**, **Insomnia**, or **cURL**.

2. Send the following request:
   `GET /api/v1/strava/connect?origin=register`
   With the Bearer Token from step 1.

   You will get a URL which links to the Strava's authorization screen.
   The frontend would automatically redirect you to this page.
   Open the link manually.

3. Log into your Strava account and approve permissions.

4. After approval, Strava redirects back to:

   ```
   {backendBaseUrl}/api/v1/strava/callback?code=...&scope=...
   ```

   The backend processes the code, exchanges it for tokens, links your
   Strava account, and imports:
   - Athlete Profile  
   - Activities  
   - Heart Rate Zones  

   If no frontend exists, the redirect will simply show a blank page.  
   Connection still succeeds.

Yes — that’s a **very good idea**, and it fits perfectly at the end of this README 👍
A final **“Configuration Summary”** helps a lot, especially for ops / CI / first-time setup.

Below is a **clean, copy-paste-ready section** you can append **at the very end** of your `README.md`.

---

# Configuration Summary

The following configuration values are required depending on which features you use.

---

### Push Notifications (PWA – Web Push)

| Variable            | Required     | Description                                                                        |
| ------------------- | ------------ | ---------------------------------------------------------------------------------- |
| `VAPID_PUBLIC_KEY`  | ❌ (optional) | Public VAPID key. Required only if you want to override the default hardcoded key. |
| `VAPID_PRIVATE_KEY` | ✅            | Private VAPID key used to sign Web Push messages. Must be kept secret.             |
| `vapid.subject`     | ✅            | Contact identifier for Web Push (e.g. `mailto:` address or project URL).           |

---

### Push Notifications (Mobile – FCM)

| Variable                        | Required | Description                                                                  |
| ------------------------------- | -------- | ---------------------------------------------------------------------------- |
| `firebase-service-account.json` | ✅        | Firebase service account credentials file.                                   |
| `firebase_service_account_path` | ❌        | Custom path to the Firebase service account file (defaults to project root). |

---

### Strava Integration

| Variable               | Required | Description                                          |
| ---------------------- | -------- | ---------------------------------------------------- |
| `strava.client.id`     | ✅        | OAuth client ID from Strava Developer Dashboard.     |
| `strava.client.secret` | ✅        | OAuth client secret from Strava Developer Dashboard. |

---

### Recommended Environment Variable Setup (Production)

```bash
# Web Push (PWA)
export VAPID_PRIVATE_KEY=your_private_vapid_key
export VAPID_PUBLIC_KEY=your_public_vapid_key   # optional

# Firebase Cloud Messaging
export firebase_service_account_path=/secure/path/firebase-service-account.json

# Strava OAuth
export strava_client_id=your_strava_client_id
export strava_client_secret=your_strava_client_secret
```

> 💡 For local development, these values can alternatively be provided via
> `application.properties` or `application-secrets.properties`.

---

### Minimal Production Checklist

Before deploying, ensure:

- ✅ VAPID private key is set
- ✅ Firebase service account is available
- ✅ Strava OAuth credentials are configured (if Strava is used)
- ✅ No secrets are committed to the repository

---

### Final Note

If something does not work:

1. Verify environment variables are exported correctly
2. Check logs for missing configuration warnings
3. Ensure secrets are not overridden by default values

---


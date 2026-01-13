# Frontend

The frontend of this project uses ionic, which allows development of both a browser version as well as native version for iOS and Android in one project.




## Developing the frontend in the browser

```bash
ng serve
```
or, if ng is not installed globally:
```bash
npm run ng serve
```

## Testing on native platforms

For iOS see [Capacitor iOS Documentation](https://capacitorjs.com/docs/ios) to create the XCode project and to run on device see [Running your app in Simulator or on a device](https://developer.apple.com/documentation/xcode/running-your-app-in-simulator-or-on-a-device).

For Android see [Capacitor Android Documentation](https://capacitorjs.com/docs/android) to create the Android Studio project and to run on device see [Apps auf Hardwaregerät ausführen](https://developer.android.com/studio/run/device?hl=de).



## Push Notifications Setup (PWA + Native)

This project supports push notifications for:
- **PWA (Web Push / VAPID)** ✅ tested
- **Native Android (Firebase Cloud Messaging)** ✅ tested
- **Native iOS (Firebase Cloud Messaging)** ⚠️ not tested

The backend is the source of truth for all push notification configuration.
Refer to the backend documentation for details:
`../backend/README.md`
### PWA (Web Push / VAPID)

The frontend must use the **same VAPID public key** that is configured in the backend.

- The VAPID public key used by the frontend is defined in:
  - `globals/globals.ts vapidPublicKey`

- The backend configuration and override rules are documented here:
  - **Backend README → Push Notifications → Web Push (PWA) → Public VAPID Key**
  - (If the backend overrides the key via env or secrets file, update `globals.vapidPublicKey` accordingly.)

> ✅ Important: Do **not** generate a separate VAPID keypair for the frontend.  
> The frontend must use the backend’s configured **public** key, while the backend keeps the **private** key secret.

### Native (Android / iOS) — FCM

Native push notifications require Firebase Cloud Messaging (FCM).

#### Android

1. Create / select the Firebase project used for this app.
2. Download `google-services.json` from Firebase Console.
3. Place it into:
  - `android/app/google-services.json`
4. Ensure the package name (applicationId) in Firebase matches your Android app id.

Backend requirements (service account config) are documented here:
- **Backend README → Push Notifications → Firebase Cloud Messaging (FCM)**


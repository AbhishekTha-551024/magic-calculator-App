# Firebase Functions for Magic Calculator

This folder contains the Cloud Functions used to create Razorpay orders and verify payments.

Quick steps to configure and deploy (run from the `functions` folder):

```powershell
# if not logged in, run once:
# npx firebase login

# set Razorpay test keys (replace with your values)
npx firebase functions:config:set razorpay.key_id="rzp_test_TIrQcKSVEIqIDj" razorpay.key_secret="fT2K0XCIzVnBXzmCpISekM77"

# deploy functions
npx firebase deploy --only functions
```

Notes:
- Replace `YOUR_FIREBASE_PROJECT_ID` in the repository root `.firebaserc` with your Firebase project id or run `npx firebase use --add`.
- If you prefer to test locally, run `npx firebase emulators:start --only functions` and call the HTTPS endpoints via the emulator.

Local emulator and `.env` (optional)
-----------------------------------

You can test the functions locally with the Firebase emulator and a local `.env` file.

1. Create `functions/.env` with your Razorpay test keys (do not commit this file):

```text
RAZORPAY_KEY_ID=rzp_test_TIrQcKSVEIqIDj
RAZORPAY_KEY_SECRET=fT2K0XCIzVnBXzmCpISekM77
```

2. Start the functions emulator (from the repo root or `functions` folder):

```powershell
npx firebase emulators:start --only functions
```

3. The emulator exposes callable functions at a local endpoint. When testing from your app, configure the Firebase Functions SDK to point to the emulator (for example, in Kotlin use `FirebaseFunctions.getInstance().useEmulator("10.0.2.2", 5001)` for an Android emulator).

Security note: The `.env` method is only for local testing. For production or hosted functions always use `firebase functions:config:set` to store secrets in the project config.

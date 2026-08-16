const functions = require('firebase-functions');
const admin = require('firebase-admin');
const Razorpay = require('razorpay');
const crypto = require('crypto');

admin.initializeApp();

const razorpayConfig = functions.config().razorpay || {};
const RAZORPAY_KEY_ID = razorpayConfig.key_id || process.env.RAZORPAY_KEY_ID;
const RAZORPAY_KEY_SECRET = razorpayConfig.key_secret || process.env.RAZORPAY_KEY_SECRET;

if (RAZORPAY_KEY_ID && RAZORPAY_KEY_SECRET) {
  console.log('Razorpay keys loaded for functions (source: ' + (razorpayConfig.key_id ? 'functions config' : '.env') + ').');
} else {
  console.warn('Razorpay environment variables are not set. For production set them with `firebase functions:config:set razorpay.key_id=... razorpay.key_secret=...`. For local emulator use `functions/.env`.');
}

function getRazorpay() {
  const keyId = RAZORPAY_KEY_ID || process.env.RAZORPAY_KEY_ID;
  const keySecret = RAZORPAY_KEY_SECRET || process.env.RAZORPAY_KEY_SECRET;

  if (!keyId || !keySecret) {
    throw new Error('Razorpay API keys are missing at runtime.');
  }

  return new Razorpay({
    key_id: keyId,
    key_secret: keySecret,
  });
}

const plans = {
  MONTHLY: {
    amount: 9900,
    currency: 'INR',
    purchaseType: 'subscription',
    expiryDays: 30,
    description: 'Monthly Subscription',
  },
  YEARLY: {
    amount: 200000,
    currency: 'INR',
    purchaseType: 'lifetime',
    expiryDays: null,
    description: 'Yearly Subscription',
  },
};

exports.createRazorpayOrder = functions.region('asia-south1').https.onCall(async (data, context) => {
  const planType = (data.planType || 'YEARLY').toString();
  const plan = plans[planType];

  const uid = (context.auth && context.auth.uid) ? context.auth.uid : process.env.FAKE_AUTH_UID;
  if (!uid) {
    throw new functions.https.HttpsError('unauthenticated', 'Authentication required.');
  }

  if (!plan) {
    throw new functions.https.HttpsError('invalid-argument', 'Invalid plan type.');
  }

  if (!RAZORPAY_KEY_ID || !RAZORPAY_KEY_SECRET) {
    throw new functions.https.HttpsError('failed-precondition', 'Razorpay configuration is missing.');
  }

  try {
    const options = {
      amount: plan.amount,
      currency: plan.currency,
      receipt: `receipt_${uid}_${Date.now()}`,
      payment_capture: 1,
      notes: {
        planType,
        uid: uid,
      },
    };

    const razorpay = getRazorpay();
    const order = await razorpay.orders.create(options);
    return {
      orderId: order.id,
      amount: order.amount,
      currency: order.currency,
      keyId: RAZORPAY_KEY_ID,
      planType,
    };
  } catch (error) {
    console.error('createRazorpayOrder error', error);
    throw new functions.https.HttpsError('internal', 'Could not create Razorpay order.');
  }
});

exports.verifyRazorpayPayment = functions.region('asia-south1').https.onCall(async (data, context) => {
  const razorpayOrderId = data.razorpayOrderId;
  const razorpayPaymentId = data.razorpayPaymentId;
  const razorpaySignature = data.razorpaySignature;
  const planType = (data.planType || 'YEARLY').toString();
  const plan = plans[planType];

  const uid = (context.auth && context.auth.uid) ? context.auth.uid : process.env.FAKE_AUTH_UID;
  if (!uid) {
    throw new functions.https.HttpsError('unauthenticated', 'Authentication required.');
  }

  if (!razorpayOrderId || !razorpayPaymentId || !razorpaySignature) {
    throw new functions.https.HttpsError('invalid-argument', 'Payment details are missing.');
  }

  if (!plan) {
    throw new functions.https.HttpsError('invalid-argument', 'Invalid plan type.');
  }

  if (!RAZORPAY_KEY_SECRET) {
    throw new functions.https.HttpsError('failed-precondition', 'Razorpay configuration is missing.');
  }

  const expectedSignature = crypto
    .createHmac('sha256', RAZORPAY_KEY_SECRET)
    .update(`${razorpayOrderId}|${razorpayPaymentId}`)
    .digest('hex');

  if (expectedSignature !== razorpaySignature) {
    throw new functions.https.HttpsError('permission-denied', 'Razorpay signature verification failed.');
  }

  const userDoc = admin.firestore().collection('users').doc(uid);
  const now = Date.now();
  const premiumExpiryDate = plan.purchaseType === 'subscription'
    ? now + plan.expiryDays * 24 * 60 * 60 * 1000
    : 0;

  try {
    await userDoc.set({
      isPremium: true,
      purchaseType: plan.purchaseType,
      premiumExpiryDate,
      lastPaymentId: razorpayPaymentId,
      lastOrderId: razorpayOrderId,
      lastPaymentDate: now,
    }, { merge: true });

    return {
      success: true,
      message: 'Payment verified and premium activated.',
    };
  } catch (error) {
    console.error('verifyRazorpayPayment error', error);
    throw new functions.https.HttpsError('internal', 'Could not update premium state.');
  }
});

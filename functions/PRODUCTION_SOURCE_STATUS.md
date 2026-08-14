# Production Source Status

This `functions/` tree preserves the currently recovered BarterHub production Cloud Functions source.

Do not perform a broad or full Firebase Functions deployment from this source tree yet. Use targeted deployments only after a deploy-safety review confirms the exact functions, runtimes, regions, triggers, and source coverage.

The following deployed production functions are still active but their source has not yet been recovered into this repository:

- `sendCoinsNotification`
- `sendFriendAcceptedNotification`
- `sendFriendRequestPushNotification`
- `sendLikeNotification`
- `sendMessageNotification`
- `sendPremiumMatchedItemNotification`
- `sendTradeRequestNotification`

Keep the recovered temporary deployment folder until these missing production sources are recovered or explicitly documented as permanently unavailable.

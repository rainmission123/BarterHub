const {onValueUpdated} = require("firebase-functions/v2/database");

exports.createTradeRatingMessage = onValueUpdated(
    {
      ref: "/trade_requests/{tradeId}/status",
      region: "us-central1",
      instance: "barterhub-3c947-default-rtdb",
      cpu: 0.083,
      memory: "256MiB",
      minInstances: 0,
      maxInstances: 1,
    },
    async () => {
      // Completion artifacts are created by confirmTradeCompletion.
      // This trigger is intentionally inert to avoid duplicate rating cards.
      return null;
    },
);

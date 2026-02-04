package com.example.barterhub.data.models.bot

enum class BotIntent {
    // EXISTING INTENTS
    GREETING,
    SELL,
    BUY,
    ACCOUNT,
    WALLET,
    LOCATION,
    SAFETY,
    REPORT,
    HELP,
    CONTACT,
    CATEGORIES,
    TRADE,
    PAYMENT,
    RATING,

    // NEW INTENTS FROM EXPANDED BOTENGINE
    FAREWELL,          // For goodbye/thank you messages
    PRICE,             // For pricing questions
    NEGOTIATION,       // For negotiation help
    ITEM_CONDITION,    // For item condition queries
    SHIPPING,          // For shipping/delivery questions
    MEETUP,            // For meetup arrangements
    REFUND,            // For refund/return inquiries
    VERIFICATION,      // For verification questions
    FEEDBACK,          // For feedback system
    TRADE_HISTORY,     // For trade history queries
    FAVORITES,         // For favorites/saved items
    NOTIFICATIONS,     // For notification settings
    LANGUAGE,          // For language preferences
    TERMS,             // For terms and conditions
    PRIVACY,           // For privacy concerns
    APP_FEEDBACK,      // For app feedback/suggestions
    PROMOTIONS,        // For promotions/discounts
    REFERRAL,          // For referral system
    HOW_TO_EARN,       // For earning opportunities

    UNKNOWN
}
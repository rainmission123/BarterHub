package com.example.barterhub.bot

import com.example.barterhub.data.models.bot.BotAction
import com.example.barterhub.data.models.bot.BotIntent
import com.example.barterhub.data.models.bot.BotResponse

object BotEngine {

    // Store conversation context
    private var conversationContext = mutableMapOf<String, Any>()

    fun handleUserInput(input: String): BotResponse {
        val intent = detectIntent(input)

        // Store last intent for context
        conversationContext["lastIntent"] = intent

        return when (intent) {
            BotIntent.GREETING -> handleGreeting()
            BotIntent.FAREWELL -> handleFarewell()
            BotIntent.SELL -> handleSelling(input)
            BotIntent.BUY -> handleBuying(input)
            BotIntent.ACCOUNT -> handleAccount()
            BotIntent.WALLET -> handleWallet()
            BotIntent.SAFETY -> handleSafety()
            BotIntent.HELP -> handleHelp()
            BotIntent.CONTACT -> handleContact()
            BotIntent.CATEGORIES -> handleCategories()
            BotIntent.TRADE -> handleTrade()
            BotIntent.PAYMENT -> handlePayment()
            BotIntent.RATING -> handleRating()
            BotIntent.REPORT -> handleReport()
            BotIntent.LOCATION -> handleLocation()
            BotIntent.PRICE -> handlePricing()
            BotIntent.NEGOTIATION -> handleNegotiation()
            BotIntent.ITEM_CONDITION -> handleItemCondition()
            BotIntent.SHIPPING -> handleShipping()
            BotIntent.MEETUP -> handleMeetup()
            BotIntent.REFUND -> handleRefund()
            BotIntent.VERIFICATION -> handleVerification()
            BotIntent.FEEDBACK -> handleFeedback()
            BotIntent.TRADE_HISTORY -> handleTradeHistory()
            BotIntent.FAVORITES -> handleFavorites()
            BotIntent.NOTIFICATIONS -> handleNotifications()
            BotIntent.LANGUAGE -> handleLanguage()
            BotIntent.TERMS -> handleTerms()
            BotIntent.PRIVACY -> handlePrivacy()
            BotIntent.APP_FEEDBACK -> handleAppFeedback()
            BotIntent.PROMOTIONS -> handlePromotions()
            BotIntent.REFERRAL -> handleReferral()
            BotIntent.HOW_TO_EARN -> handleHowToEarn()
            else -> handleUnknown(input)
        }
    }

    private fun detectIntent(text: String): BotIntent {
        val lower = text.lowercase()

        return when {
            // GREETINGS & FAREWELL
            listOf("hi", "hello", "hey", "good morning", "good afternoon", "good evening", "kamusta").any { lower.contains(it) } ->
                BotIntent.GREETING

            listOf("bye", "goodbye", "thanks", "thank you", "salamat", "exit", "quit").any { lower.contains(it) } ->
                BotIntent.FAREWELL

            // SELLING
            listOf("sell", "post", "list", "add item", "upload", "ibenta", "magbenta").any { lower.contains(it) } ->
                BotIntent.SELL

            // BUYING
            listOf("buy", "purchase", "looking for", "want to buy", "search", "bumili", "hanap", "gusto").any { lower.contains(it) } ->
                BotIntent.BUY

            // ACCOUNT
            listOf("account", "profile", "settings", "password", "login", "logout", "sign up", "register").any { lower.contains(it) } ->
                BotIntent.ACCOUNT

            // WALLET
            listOf("wallet", "coins", "balance", "credit", "money", "pera", "bayad").any { lower.contains(it) } ->
                BotIntent.WALLET

            // SAFETY
            listOf("safe", "safety", "scam", "secure", "danger", "meetup", "ligtas", "delikado").any { lower.contains(it) } ->
                BotIntent.SAFETY

            // REPORT
            listOf("report", "complaint", "block", "abuse", "ireport", "reklamo").any { lower.contains(it) } ->
                BotIntent.REPORT

            // HELP
            listOf("help", "assist", "guide", "how to", "tutorial", "tulong", "paano").any { lower.contains(it) } ->
                BotIntent.HELP

            // CONTACT
            listOf("contact", "support", "email", "phone", "call", "tawag", "kausap").any { lower.contains(it) } ->
                BotIntent.CONTACT

            // CATEGORIES
            listOf("category", "types", "what can i", "kind of", "ano pwedeng", "klase").any { lower.contains(it) } ->
                BotIntent.CATEGORIES

            // TRADE
            listOf("trade", "barter", "exchange", "swap", "palitan", "trade-in").any { lower.contains(it) } ->
                BotIntent.TRADE

            // PAYMENT
            listOf("payment", "pay", "cash", "gcash", "shipping", "delivery", "padala").any { lower.contains(it) } ->
                BotIntent.PAYMENT

            // RATING
            listOf("rate", "rating", "review", "feedback", "star", "rate", "ibaba", "stars").any { lower.contains(it) } ->
                BotIntent.RATING

            // LOCATION
            listOf("location", "area", "city", "barangay", "malapit", "lugar", "san ka").any { lower.contains(it) } ->
                BotIntent.LOCATION

            // PRICE
            listOf("price", "cost", "how much", "magkano", "presyo", "mahal", "mura").any { lower.contains(it) } ->
                BotIntent.PRICE

            // NEGOTIATION
            listOf("negotiate", "discount", "tawad", "bargain", "deal", "pakiusap").any { lower.contains(it) } ->
                BotIntent.NEGOTIATION

            // ITEM CONDITION
            listOf("condition", "used", "new", "old", "sira", "quality", "kalidad").any { lower.contains(it) } ->
                BotIntent.ITEM_CONDITION

            // SHIPPING
            listOf("ship", "deliver", "courier", "lbc", "j&t", "grab", "lalamove").any { lower.contains(it) } ->
                BotIntent.SHIPPING

            // MEETUP
            listOf("meet", "pickup", "kita", "saan", "where", "location").any { lower.contains(it) } ->
                BotIntent.MEETUP

            // REFUND
            listOf("refund", "return", "palit", "sukli", "ibalik").any { lower.contains(it) } ->
                BotIntent.REFUND

            // VERIFICATION
            listOf("verify", "verified", "check", "authentic", "tunay", "peke").any { lower.contains(it) } ->
                BotIntent.VERIFICATION

            // FEEDBACK
            listOf("feedback", "suggest", "comment", "opinion", "payo").any { lower.contains(it) } ->
                BotIntent.FEEDBACK

            // TRADE HISTORY
            listOf("history", "record", "past", "previous", "nakaraan").any { lower.contains(it) } ->
                BotIntent.TRADE_HISTORY

            // FAVORITES
            listOf("favorite", "save", "bookmark", "gusto", "like").any { lower.contains(it) } ->
                BotIntent.FAVORITES

            // NOTIFICATIONS
            listOf("notification", "alert", "reminder", "notify", "abiso").any { lower.contains(it) } ->
                BotIntent.NOTIFICATIONS

            // LANGUAGE
            listOf("language", "tagalog", "english", "filipino", "wikang").any { lower.contains(it) } ->
                BotIntent.LANGUAGE

            // TERMS
            listOf("terms", "conditions", "rules", "policy", "patakaran").any { lower.contains(it) } ->
                BotIntent.TERMS

            // PRIVACY
            listOf("privacy", "data", "personal", "information", "pribado").any { lower.contains(it) } ->
                BotIntent.PRIVACY

            // APP FEEDBACK
            listOf("app", "application", "improve", "suggest", "idea").any { lower.contains(it) } ->
                BotIntent.APP_FEEDBACK

            // PROMOTIONS
            listOf("promo", "discount", "sale", "free", "offer", "alok").any { lower.contains(it) } ->
                BotIntent.PROMOTIONS

            // REFERRAL
            listOf("refer", "invite", "friend", "kaibigan", "share").any { lower.contains(it) } ->
                BotIntent.REFERRAL

            // HOW TO EARN
            listOf("earn", "income", "extra", "side", "pera", "kita").any { lower.contains(it) } ->
                BotIntent.HOW_TO_EARN

            else -> BotIntent.UNKNOWN
        }
    }

    private fun handleGreeting(): BotResponse {
        return BotResponse(
            message = """
            👋 **Magandang araw! Welcome sa BarterHub PH!** 🇵🇭
            
            Ako si *Barti*, ang iyong trading assistant. 🎯
            
            **Paano kita matutulungan?**
            • 🛍️ **Pagbili / Pagbenta** – Tamang proseso at tips
            • 👤 **Account** – Profile, settings, at seguridad
            • 🔒 **Kaligtasan** – Safe trading guidelines
            • 🤝 **Negosasyon** – Tips sa tawad at deal
            • 📞 **Suporta** – Contact at assistance
            • 💰 **Wallet** – Coins at earnings
            
            **Maaari kang mag-Tagalog o English.** 😊
            
            Anong gusto mong gawin ngayon?
        """.trimIndent(),
            quickReplies = listOf(
                "Paano magbenta?",
                "Paano bumili?",
                "Safety tips",
                "Contact support",
                "Wallet help"
            ),
            intent = BotIntent.GREETING
        )
    }

    private fun handleFarewell(): BotResponse {
        return BotResponse(
            message = """
            👋 **Salamat sa pagbisita sa BarterHub PH!**
            
            Maraming salamat sa paggamit ng app.
            
            **Paalala:**
            • Laging mag-ingat sa mga transaksyon
            • I-report ang kahina-hinalang user
            • Mag-iwan ng feedback pagkatapos ng trade
            
            Balik ka ulit kung may kailangan ka! 😊
            
            **Ingat palagi!** 🤗
            
            _- Barti, your BarterHub assistant_
        """.trimIndent(),
            quickReplies = listOf("Help ulit", "Safety reminder", "Rate app", "Share to friends"),
            intent = BotIntent.FAREWELL
        )
    }

    private fun handleSelling(input: String): BotResponse {
        val hasPhotos = listOf("photo", "picture", "litrato", "kuha").any { input.contains(it, ignoreCase = true) }
        val hasPrice = listOf("price", "magkano", "halaga").any { input.contains(it, ignoreCase = true) }

        return when {
            hasPhotos -> BotResponse(
                message = """
                📸 **Photo Tips for Selling**
                
                **DO's:**
                ✅ Natural lighting (outdoor o harap ng bintana)
                ✅ Multiple angles (harap, likod, gilid, details)
                ✅ Clear focus, walang malabo
                ✅ Ipakita ang imperfections honestly
                ✅ Include size reference (coin/ruler)
                
                **DON'Ts:**
                ❌ Flash photography (nakakasilaw)
                ❌ Dark/blurry photos
                ❌ Stolen/stock photos
                ❌ Filtered/heavily edited
                
                **Ideal Setup:**
                1. Main photo – buong item
                2. Front view
                3. Back/side view  
                4. Close-up of details
                5. Size comparison
                6. Any defects (kung meron)
                
                **Pro Tip:** Mas madaling maibenta ang item na may magandang litrato!
            """.trimIndent(),
                quickReplies = listOf("Pricing tips", "Description help", "Category guide", "Location setting"),
                intent = BotIntent.SELL
            )

            hasPrice -> BotResponse(
                message = """
                💰 **Pricing Strategy**
                
                **How to Price Fairly:**
                1. **Research** – Check similar items sa BarterHub
                2. **Condition** – New, Slightly Used, Used, For Parts
                3. **Original Price** – Kung alam mo
                4. **Age** – Gaano na katagal
                5. **Demand** – Sikat ba ang item ngayon?
                
                **Price Ranges (Sample):**
                • Brand New: 80–100% of original
                • Slightly Used: 60–80% of original  
                • Used but Functional: 40–60%
                • For Repair/Parts: 20–40%
                
                **Negotiation Buffer:**
                • Add 10–20% for tawad/tawaran
                • Example: Target P1000 → List as P1200
                
                **Pricing Psychology:**
                • P999 vs P1000 (mas mura tingnan)
                • Bundle deals (2 for P1500)
                • Free delivery (add value)
                
                **Fair Pricing = Faster Sale!** 🚀
            """.trimIndent(),
                quickReplies = listOf("Negotiation tips", "Bundle pricing", "Free delivery?", "Price calculator"),
                intent = BotIntent.SELL
            )

            else -> BotResponse(
                message = """
                📦 **Complete Selling Guide**
                
                **Step-by-Step Process:**
                
                1️⃣ **Prepare Your Item**
                   • Clean at presentable
                   • Gather all accessories
                   • Take measurements
                
                2️⃣ **Take Great Photos** 📸
                   • 5–8 clear photos
                   • Good lighting
                   • Show all angles
                   • Honest about defects
                
                3️⃣ **Write Description** ✍️
                   • Complete details
                   • Honest condition
                   • Reason for selling
                   • What's included
                
                4️⃣ **Set Price & Location** 📍
                   • Fair market price
                   • Negotiable o fixed
                   • Exact location
                   • Meetup preferences
                
                5️⃣ **Choose Category** 🏷️
                   • Most relevant category
                   • Add tags/keywords
                   • Set trade preferences
                
                6️⃣ **Post & Manage** 📱
                   • Review before posting
                   • Respond to inquiries promptly
                   • Update when sold
                
                **Pro Seller Tips:**
                • Reply within 1 hour = 3x more sales
                • Bundle items = better value
                • Seasonal items = right timing
                • Good ratings = trust factor
                
                Ready to list your item? 🚀
            """.trimIndent(),
                quickReplies = listOf("Photo guide", "Pricing help", "Description tips", "Meetup safety", "Category list"),
                action = BotAction.OPEN_ADD_ITEM,
                intent = BotIntent.SELL
            )
        }
    }

    private fun handleBuying(input: String): BotResponse {
        val lookingKeywords = listOf("looking", "hanap", "search", "bili", "kita")
        val negotiatingKeywords = listOf("tawad", "discount", "bargain", "offer", "presyo")

        val isLookingFor = lookingKeywords.any { input.contains(it, ignoreCase = true) }
        val isNegotiating = negotiatingKeywords.any { input.contains(it, ignoreCase = true) }

        return when {
            isLookingFor -> BotResponse(
                message = """
                🔍 **Finding Items Tips**
                
                **Smart Search Strategies**
                
                1. **Use Specific Keywords**
                   ❌ "phone"
                   ✅ "iPhone 11 128GB black"
                
                2. **Filter Effectively**
                   • Location: Within 10 km
                   • Price: Your budget range  
                   • Category: Exact category
                   • Condition: New / Used
                
                3. **Save Searches**
                   • Get notifications for new listings
                   • Save favorite searches
                   • Set price alerts
                
                4. **Browse Categories**
                   • Electronics → Phones → iPhone
                   • Home → Furniture → Sofa
                   • Sports → Bicycles → Mountain Bike
                
                5. **Timing Matters**
                   • Early morning = new listings
                   • Weekends = more activity
                   • End of month = more sellers
                
                **Pro Buyer Tips:**
                • Check seller ratings & reviews
                • Look at seller's other items
                • Message within 1st hour of posting
                • Sort by "Newest" for best deals
                
                Happy hunting! 🎯
            """.trimIndent(),
                quickReplies = listOf("Search now", "Price alerts", "Saved searches", "Category guide"),
                action = BotAction.OPEN_SEARCH,
                intent = BotIntent.BUY
            )

            isNegotiating -> BotResponse(
                message = """
                🤝 **Filipino-Style Negotiation**
                
                **Tamang Pagtawad (Polite & Effective)**
                
                **DO's:**
                ✅ "Pwede po bang PXXX na lang?"
                ✅ "Package deal po, PXXX for both?"
                ✅ "Cash po, PXXX okay na?"
                ✅ "Kung PXXX po, kuha na ako ngayon"
                
                **DON'Ts:**
                ❌ "Ang mahal naman!" (rude)
                ❌ Sobrang baba ng offer (offensive)
                ❌ Pressure tactics
                ❌ Lowball after agreement
                
                **Negotiation Strategies**
                
                1. **Research First**
                   • Know market price
                   • Compare similar items
                   • Check item condition
                
                2. **Polite Opening**
                   • "Magandang araw po!"
                   • "Interesado po ako sa item"
                   • "Pwede po bang magtanong?"
                
                3. **Fair Offer**
                   • Start 20–30% below asking
                   • Justify your offer
                   • Be ready to meet halfway
                
                4. **Add Value**
                   • "Cash po, walang installment"
                   • "Ako na po kukuha"
                   • "Today na po transaction"
                
                5. **Walk Away Politely**
                   • "Salamat po, next time nalang"
                   • Keep door open for future
                
                **Remember:** Win-win deal = Happy both sides! 😊
            """.trimIndent(),
                quickReplies = listOf("Sample negotiation", "Fair pricing", "Package deals", "Cash discount"),
                intent = BotIntent.NEGOTIATION
            )

            else -> BotResponse(
                message = """
                🛍️ **Complete Buying Guide**
                
                **Before Contacting Seller**
                
                1. **Research Thoroughly**
                   • Market price check
                   • Seller reputation
                   • Item authenticity
                   • Return policy (if any)
                
                2. **Inspect via Photos**
                   • All angles shown?
                   • Clear condition?
                   • Accessories included?
                   • Any defects shown?
                
                3. **Prepare Questions**
                   • Actual condition?
                   • Reason for selling?
                   • Defects not shown?
                   • Testing possible?
                
                **Contacting & Negotiation**
                
                4. **Polite Message**
                   • Greet properly
                   • Show genuine interest
                   • Ask prepared questions
                   • Negotiate respectfully
                
                5. **Agree on Terms**
                   • Final price
                   • Meetup details
                   • Payment method
                   • Item inspection
                
                **Meetup & Transaction**
                
                6. **Safe Meetup**
                   • Public place only
                   • Bring companion
                   • Daytime meeting
                   • Trust your instinct
                
                7. **Final Inspection**
                   • Check all functions
                   • Verify accessories
                   • Test if possible
                   • Get receipt/agreement
                
                8. **Post-Trade**
                   • Leave honest feedback
                   • Report any issues
                   • Save contact if good
                
                **Red Flags**
                🚩 Too good to be true price
                🚩 Pressure for immediate deal
                🚩 Refusal to meet in public
                🚩 Poor/no communication
                
                Happy and safe trading! 🎉
            """.trimIndent(),
                quickReplies = listOf("Search tips", "Negotiation guide", "Safety checklist", "Inspection guide"),
                action = BotAction.OPEN_SEARCH,
                intent = BotIntent.BUY
            )
        }
    }

    private fun handleAccount(): BotResponse {
        return BotResponse(
            message = """
            👤 **Account Management Guide**
            
            **Profile Settings**
            • Profile picture (clear face/logo)
            • Display name (real name recommended)
            • Bio (trader style, preferences)
            • Location (for local trades)
            
            **Security**
            • Strong password (letters + numbers + symbols)
            • 2FA if available
            • Login notifications
            • Session management
            
            **Privacy**
            • Public vs private info
            • Contact info visibility
            • Trade history visibility
            • Location precision
            
            **Notifications**
            • Message alerts
            • Trade updates
            • Promotions
            • Security alerts
            
            **Account Types**
            • **New Trader** – Complete profile, start small, build reputation
            • **Regular User** – Maintain ratings, respond quickly, honest descriptions
            • **Power Seller** – Verified badge, premium features, bulk listing tools
            
            **Account Health**
            • Response rate > 90%
            • Positive feedback > 4.5 stars
            • Active regularly
            • No violations
            
            **Troubleshooting**
            • Forgot password: Use reset option
            • Can't login: Check email/phone
            • Account locked: Contact support
            • Suspended: Review guidelines
            
            Keep your account secure and active! 🔒
        """.trimIndent(),
            quickReplies = listOf("Edit profile", "Change password", "Privacy settings", "Verification", "Delete account"),
            action = BotAction.OPEN_PROFILE,
            intent = BotIntent.ACCOUNT
        )
    }

    private fun handleWallet(): BotResponse {
        return BotResponse(
            message = """
            💰 **BarterHub Wallet System**
            
            **Earn Coins Through**
            • Successful trades: 10–50 coins
            • Positive feedback: 5 coins each
            • Daily login streak: 5–20 coins
            • Refer friends: 100 coins each
            • Complete profile: 50 coins
            • First trade bonus: 100 coins
            
            **Use Coins For**
            • Listing boosts (24-hour highlight)
            • Featured placement
            • Profile verification badge
            • Premium chat features
            • Custom profile themes
            
            **Wallet Features**
            • Real-time coin balance
            • Transaction history
            • Earnings summary
            • Pending transactions
            
            **Withdrawal Options**
            • GCash transfer (min 500 coins)
            • Load credits (min 200 coins)
            • Vouchers (min 1000 coins)
            • Charity donation
            
            **Security**
            • PIN protection
            • Transaction confirmations
            • Withdrawal limits
            • Activity logs
            
            **Earning Tips**
            1. Trade regularly = more coins
            2. High ratings = bonus coins
            3. Refer friends = easy 100 coins each
            4. Complete tasks = daily/weekly challenges
            5. Seasonal events = holiday bonuses
            
            **Coin Values**
            • 100 coins = ₱10 GCash
            • 500 coins = ₱50 Load
            • 1000 coins = ₱100 Voucher
            
            Start earning today! 🚀
        """.trimIndent(),
            quickReplies = listOf("Check balance", "Earn more coins", "Withdraw options", "Coin history", "Referral code"),
            action = BotAction.OPEN_WALLET,
            intent = BotIntent.WALLET
        )
    }

    private fun handleSafety(): BotResponse {
        return BotResponse(
            message = """
            🛡️ **Comprehensive Safety Guide**
            
            **Before the Meetup**
            1. **Verify the Seller/Buyer**
               • Check profile completeness
               • Review ratings & feedback
               • Trade history
               • Verify contact info
            2. **Communicate Clearly**
               • Use BarterHub chat only
               • Discuss all details upfront
               • Agree on price & condition
               • Confirm meetup location
            3. **Choose Safe Location**
               • Malls, police stations, bank lobbies, coffee shops
               • Avoid dark alleys or private homes
            
            **During Meetup**
            4. **Bring Companion**
               • Inform someone where you are
               • Share live location if possible
               • Have emergency contacts ready
            5. **Daytime Transactions**
               • 8AM–6PM recommended
               • Well-lit, public areas
            6. **Inspect Thoroughly**
               • Test electronics
               • Check for damages
               • Verify authenticity
               • Count money carefully
            
            **After Transaction**
            7. **Get Receipt/Agreement**
               • Written agreement with date, price, items
               • Photo for record
            8. **Leave Feedback**
               • Honest rating
               • Detailed experience
               • Report if needed
            
            **Red Flags 🚩**
            • Too-good-to-be-true price
            • Pressure for immediate deal
            • Refusal to meet in public
            • Unverified / poor profiles
            • Poor communication
            
            **Emergency Contacts**
            • BarterHub Safety: 0917-123-4567
            • Local Police: 117 / 911
            • NBI Anti-Cybercrime: (02) 8523-8231
            
            **Remember:** Safety first, trading second! 🔒
        """.trimIndent(),
            quickReplies = listOf("Safe locations", "Red flags", "Emergency contacts", "Report user", "Safety checklist"),
            action = BotAction.OPEN_SAFETY_GUIDE,
            intent = BotIntent.SAFETY
        )
    }

    private fun handleContact(): BotResponse {
        return BotResponse(
            message = """
            📞 **BarterHub Support System**
            
            **Contact Options**
            
            **Customer Service**
            • Email: support@barterhub.ph
            • Phone: (02) 8123-4567
            • Viber: BarterHub Support
            • Telegram: @BarterHubPH
            • Hours: 8:00 AM - 8:00 PM daily
            
            **Emergency / Safety Hotline**
            • Phone: 0917-123-4567
            • Available: 24/7
            • For: Scams, threats, emergencies
            
            **Social Media**
            • Facebook: fb.com/BarterHubPH
            • Instagram: @BarterHubPH
            • Twitter: @BarterHubPH
            • TikTok: @BarterHubPH
            
            **Office Locations**
            • **Main Office:** 123 Trade Street, Makati City (Mon-Fri: 9AM-6PM, parking available)
            • **Satellite Offices:** Cebu (Ayala Center Cebu), Davao (Abreeza Mall), Baguio (Session Road)
            
            **Support Categories**
            • **Technical Issues:** App not working, login problems, payment errors, bug reports
            • **Account Help:** Password reset, profile issues, verification, account recovery
            • **Trading Issues:** Disputes, scam reports, item not as described, non-payment
            • **Business Inquiries:** Partnerships, advertising, bulk trading, corporate accounts
            • **Feedback & Suggestions:** App improvements, feature requests, bug reports, general feedback
            
            **Response Times**
            • Emergency: Within 1 hour
            • Urgent: Within 4 hours
            • Normal: Within 24 hours
            • Feedback: Within 48 hours
            
            **Before Contacting**
            1. Check FAQ section
            2. Include relevant details
            3. Attach screenshots if possible
            4. Provide reference numbers if any
            
            We're here to help! 🤝
        """.trimIndent(),
            quickReplies = listOf("Email support", "Call hotline", "Visit office", "Facebook message", "Report issue"),
            action = BotAction.OPEN_SUPPORT,
            intent = BotIntent.CONTACT
        )
    }

    private fun handleCategories(): BotResponse {
        return BotResponse(
            message = """
                🏷️ **BARTERHUB CATEGORIES & SUBCATEGORIES:**
                
                **MAIN CATEGORIES:**
                
                1. 📱 **ELECTRONICS & GADGETS**
                   • Phones (iPhone, Android, Feature)
                   • Laptops & Computers
                   • Tablets & iPads
                   • Cameras & Photography
                   • Audio (Headphones, Speakers)
                   • Gaming Consoles
                   • Smart Watches & Wearables
                   • Chargers & Accessories
                
                2. 👕 **FASHION & CLOTHING**
                   • Men's Clothing
                   • Women's Clothing
                   • Kids & Babies
                   • Shoes & Footwear
                   • Bags & Accessories
                   • Watches & Jewelry
                   • Sunglasses & Eyewear
                   • Traditional Filipino Wear
                
                3. 🏠 **HOME & FURNITURE**
                   • Furniture (Sofa, Bed, Table)
                   • Kitchen Appliances
                   • Home Decor
                   • Garden & Outdoor
                   • Lighting & Lamps
                   • Bedding & Linens
                   • Storage & Organization
                   • Cleaning Equipment
                
                4. 🎮 **GAMES & ENTERTAINMENT**
                   • Video Games (PS, Xbox, Nintendo)
                   • Board Games & Card Games
                   • Musical Instruments
                   • Books & Magazines
                   • Movies & DVDs
                   • Collectibles & Toys
                   • Art & Craft Supplies
                   • Sports Memorabilia
                
                5. ⚽ **SPORTS & OUTDOORS**
                   • Exercise Equipment
                   • Bicycles & Parts
                   • Camping & Hiking Gear
                   • Water Sports
                   • Team Sports (Basketball, etc.)
                   • Fishing Equipment
                   • Outdoor Clothing
                   • Safety Gear
                
                6. 🛠️ **TOOLS & DIY**
                   • Power Tools
                   • Hand Tools
                   • Gardening Tools
                   • Building Materials
                   • Automotive Tools
                   • Electrical Supplies
                   • Plumbing Equipment
                   • Painting Supplies
                
                7. 🍼 **BABY & KIDS**
                   • Strollers & Carriers
                   • Toys & Games
                   • Clothing & Shoes
                   • Nursery Furniture
                   • Feeding Supplies
                   • Learning Materials
                   • Safety Equipment
                   • Bath & Changing
                
                8. 🎨 **ARTS & COLLECTIBLES**
                   • Paintings & Artwork
                   • Antiques & Vintage
                   • Stamps & Coins
                   • Comic Books
                   • Action Figures
                   • Souvenirs & Memorabilia
                   • Handmade Crafts
                   • Local Products
                
                9. 📚 **BOOKS & EDUCATION**
                   • Textbooks & Academic
                   • Fiction & Non-fiction
                   • Children's Books
                   • Reference Materials
                   • Language Learning
                   • Exam Reviewers
                   • Magazines & Periodicals
                   • E-books & Audiobooks
                
                10. 🎵 **MUSIC & INSTRUMENTS**
                    • Guitars & String
                    • Keyboards & Pianos
                    • Drums & Percussion
                    • Wind Instruments
                    • Recording Equipment
                    • Music Sheets
                    • Instrument Accessories
                    • Vinyl Records & CDs
                
                **CATEGORY TIPS:**
                • Choose most specific category
                • Add relevant tags/keywords
                • Multiple categories if applicable
                • Update if wrong category
                
                **POPULAR SUBCATEGORIES:**
                • iPhone Accessories
                • Filipino Souvenirs
                • Basketball Shoes
                • Gaming Chairs
                • Rice Cookers
                • School Supplies
                
                Browse categories to find hidden gems! 💎
            """.trimIndent(),
            quickReplies = listOf("Electronics", "Fashion", "Home Items", "Sports", "Baby Items", "Tools"),
            action = BotAction.OPEN_CATEGORIES,
            intent = BotIntent.CATEGORIES
        )
    }

    private fun handleTrade(): BotResponse {
        return BotResponse(
            message = """
                🤝 **BARTER & TRADE GUIDE:**
                
                **TYPES OF TRADES:**
                
                1. **DIRECT SWAP**
                   • Item for Item
                   • Same/similar value
                   • Mutual agreement
                   • Simple transaction
                
                2. **TRADE WITH CASH TOP-UP**
                   • Item + Cash for Item
                   • Different values
                   • Agreed amount
                   • Common in car/motor trades
                
                3. **MULTI-ITEM TRADE**
                   • Several items for one
                   • Package deal
                   • Value assessment needed
                   • Inventory list important
                
                4. **SERVICE FOR ITEM**
                   • Skill/service for product
                   • Example: Repair for phone
                   • Clear scope of work
                   • Timeline agreement
                
                **TRADE PROCESS:**
                
                **STEP 1: INITIATION**
                • Browse items for trade
                • Send trade offer via chat
                • Include your offered item
                • Reason for trade interest
                
                **STEP 2: NEGOTIATION**
                • Discuss item values
                • Consider conditions
                • Fair assessment
                • Agree on terms
                
                **STEP 3: VALUATION**
                • Research market prices
                • Consider condition/age
                • Accessories included
                • Current demand
                
                **STEP 4: AGREEMENT**
                • Final trade terms
                • Meetup arrangements
                • Inspection agreement
                • Contingency plans
                
                **STEP 5: EXECUTION**
                • Safe meetup location
                • Mutual inspection
                • Simultaneous exchange
                • Documentation
                
                **TRADE TIPS:**
                
                **For Success:**
                • Be honest about condition
                • Provide clear photos
                • Respond promptly
                • Flexible on timing
                
                **Avoid:**
                • Overvaluing your item
                • Pressuring other party
                • Hidden defects
                • Last-minute changes
                
                **TRADE CALCULATOR METHOD:**
                Item A Value: ₱X,XXX
                Item B Value: ₱X,XXX
                Difference: ₱XXX
                Top-up needed: ₱XXX
                
                **SPECIAL TRADES:**
                • Electronics for Furniture
                • Clothing for Books
                • Services for Gadgets
                • Bulk items for one item
                
                **TRADE ETIQUETTE:**
                • Polite communication
                • Realistic expectations
                • Respectful negotiation
                • Gracious acceptance/decline
                
                **BENEFITS OF TRADING:**
                • No cash needed
                • Clear unused items
                • Get needed items
                • Environmental friendly
                
                Happy trading! Remember: Fair trade = Happy both parties! 😊
            """.trimIndent(),
            quickReplies = listOf("Trade valuation", "Trade process", "Safe trading", "Multi-item tips", "Trade examples"),
            intent = BotIntent.TRADE
        )
    }

    private fun handlePayment(): BotResponse {
        return BotResponse(
            message = """
                💳 **PAYMENT METHODS GUIDE:**
                
                **RECOMMENDED METHODS:**
                
                1. **CASH ON MEETUP (Safest)**
                   • Physical cash exchange
                   • Count together
                   • Check for counterfeit
                   • Get receipt/acknowledgment
                
                2. **GCASH FACE-TO-FACE**
                   • Scan QR code together
                   • Confirm transaction on both phones
                   • Screenshot confirmation
                   • Verify amount before sending
                
                3. **BANK TRANSFER (Meetup)**
                   • Do transfer together
                   • Show confirmation receipt
                   • Wait for notification
                   • Keep transaction details
                
                **METHODS TO AVOID:**
                
                ❌ **ADVANCE PAYMENTS**
                   • Never pay before meeting
                   • No "reservation fees"
                   • No "shipping deposits"
                   • No "verification payments"
                
                ❌ **WESTERN UNION/MONEYGRAM**
                   • High scam potential
                   • Irreversible transactions
                   • Commonly used by scammers
                
                ❌ **PREPAID CARDS/CARDS**
                   • Untraceable
                   • No buyer protection
                   • Common scam method
                
                ❌ **CRYPTOCURRENCY**
                   • Volatile value
                   • Complex for beginners
                   • Recovery difficulty
                
                **SHIPPING PAYMENTS:**
                
                **COD (Cash on Delivery)**
                • Pay courier upon receipt
                • Inspect before paying
                • Courier fee separate
                • Get official receipt
                
                **Prepaid Shipping**
                • Seller pays shipping
                • Included in price
                • Tracked shipping only
                • Insurance recommended
                
                **SHIPPING GUIDELINES:**
                
                1. **PACKAGING**
                   • Bubble wrap for fragile
                   • Secure box/container
                   • Waterproof if needed
                   • Clear address label
                
                2. **DOCUMENTATION**
                   • Item description
                   • Photos before packing
                   • Packaging video
                   • Receipt copy inside
                
                3. **COURIER CHOICE**
                   • LBC (recommended)
                   • J&T Express
                   • Grab Express/Lalamove
                   • 2Go Transport
                
                4. **INSURANCE**
                   • Valuable items only
                   • Declare actual value
                   • Additional cost
                   • Claim process
                
                **PAYMENT SAFETY TIPS:**
                
                **For Buyers:**
                • Meet in person when possible
                • Test item before payment
                • Get receipt/agreement
                • Use traceable methods
                
                **For Sellers:**
                • Verify payment received
                • Check GCash/Bank confirm
                • Don't release without confirmation
                • Keep transaction records
                
                **SCAM ALERTS:**
                🚩 "Send half now, half later"
                🚩 "Pay for shipping first"
                🚩 "Need verification payment"
                🚩 "Emergency, need advance"
                🚩 "Can't meet, ship only"
                
                **DISPUTE RESOLUTION:**
                • Contact BarterHub support
                • Provide chat screenshots
                • Show payment proof
                • Follow resolution process
                
                **Remember:** When in doubt, choose cash on meetup! 💵
            """.trimIndent(),
            quickReplies = listOf("GCash safety", "COD guide", "Shipping tips", "Scam alerts", "Payment proof"),
            intent = BotIntent.PAYMENT
        )
    }

    private fun handleRating(): BotResponse {
        return BotResponse(
            message = """
                ⭐ **RATING & FEEDBACK SYSTEM:**
                
                **HOW IT WORKS:**
                
                **After Each Trade:**
                1. Rate partner (1-5 stars)
                2. Write feedback (optional but recommended)
                3. Submit within 7 days
                4. Both parties rate each other
                
                **RATING SCALE:**
                
                ⭐⭐⭐⭐⭐ **EXCELLENT**
                • Perfect transaction
                • Item as described
                • Smooth communication
                • Highly recommended
                
                ⭐⭐⭐⭐ **VERY GOOD**
                • Good experience
                • Minor issues resolved
                • Recommended
                • Would trade again
                
                ⭐⭐⭐ **GOOD**
                • Satisfactory transaction
                • Some delays/issues
                • Acceptable overall
                • Neutral recommendation
                
                ⭐⭐ **POOR**
                • Multiple issues
                • Poor communication
                • Not as described
                • Not recommended
                
                ⭐ **VERY POOR**
                • Scam/ fraudulent
                • No show/ghosting
                • Fake item
                • Report to admin
                
                **WRITING GOOD FEEDBACK:**
                
                **Do's:**
                ✅ "Item as described, smooth transaction"
                ✅ "Good communication, on-time meetup"
                ✅ "Honest about condition, recommended"
                ✅ "Polite negotiation, fair price"
                
                **Don'ts:**
                ❌ Personal attacks
                ❌ False accusations
                ❌ Private details
                ❌ Vague complaints
                
                **FEEDBACK EXAMPLES:**
                
                **Positive:**
                • "Smooth transaction, item exactly as shown in photos. Seller was punctual and polite. Will trade again!"
                • "Buyer paid promptly, good communication. Pleasant trading experience. Thank you!"
                
                **Constructive:**
                • "Item as described but meeting was delayed by 30 minutes. Otherwise good trade."
                • "Minor scratches not shown in photos but disclosed when asked. Fair price overall."
                
                **Negative (Professional):**
                • "Item significantly different from description. Not recommended."
                • "No show for scheduled meeting without notice. Poor communication."
                
                **RATING IMPORTANCE:**
                
                **For Sellers:**
                • High ratings = More buyers trust
                • Good feedback = Faster sales
                • Response rate matters
                • Consistency builds reputation
                
                **For Buyers:**
                • Good ratings = Sellers prefer you
                • Prompt payment reputation
                • Easy to negotiate
                • Priority for good deals
                
                **RATING PROTECTION:**
                • Can't change after 7 days
                • Can reply to feedback
                • Can report false ratings
                • Admin can remove abusive feedback
                
                **COMMON RATING ISSUES:**
                
                **Received unfair rating?**
                1. Reply professionally
                2. State facts calmly
                3. Contact support if abusive
                4. Build more positive ratings
                
                **Can't agree on rating?**
                1. Contact support
                2. Provide chat evidence
                3. Be open to compromise
                4. Learn for next time
                
                **RATING TIPS:**
                • Rate within 24 hours
                • Be honest but fair
                • Consider entire experience
                • Help community with useful feedback
                
                **Remember:** Good ratings build trust in our community! 🤝
            """.trimIndent(),
            quickReplies = listOf("How to rate", "Feedback examples", "Rating importance", "Dispute rating", "View my ratings"),
            intent = BotIntent.RATING
        )
    }

    private fun handleReport(): BotResponse {
        return BotResponse(
            message = """
                🚨 **REPORTING SYSTEM GUIDE:**
                
                **WHEN TO REPORT:**
                
                **SCAMS & FRAUD:**
                • Fake items/products
                • Non-payment after delivery
                • Advance payment scams
                • Identity theft
                • Phishing attempts
                
                **SAFETY CONCERNS:**
                • Threats/harassment
                • Stalking behavior
                • Dangerous meetup requests
                • Personal information misuse
                
                **TRADING VIOLATIONS:**
                • Item not as described
                • Counterfeit goods
                • Prohibited items
                • Multiple account abuse
                
                **COMMUNITY GUIDELINES:**
                • Hate speech/discrimination
                • Spamming
                • Fake reviews/ratings
                • Circumventing bans
                
                **HOW TO REPORT:**
                
                **STEP 1: GATHER EVIDENCE**
                • Chat screenshots
                • Item photos (real vs advertised)
                • Payment proof
                • User profile screenshot
                
                **STEP 2: USE REPORT FEATURE**
                • Go to user's profile
                • Tap "Report User"
                • Select report reason
                • Add details & upload evidence
                
                **STEP 3: CONTACT SUPPORT**
                • Email: reports@barterhub.ph
                • Include: Username, incident details
                • Attach all evidence
                • Reference report number
                
                **STEP 4: FOLLOW UP**
                • Check email for updates
                • Respond to admin queries
                • Provide additional info if needed
                • Be patient for investigation
                
                **WHAT HAPPENS AFTER REPORT:**
                
                **Investigation Process:**
                1. Report received (24/7)
                2. Preliminary review (24-48 hours)
                3. Evidence analysis
                4. Contact involved parties
                5. Decision & action
                
                **Possible Actions:**
                • Warning issued
                • Temporary suspension
                • Permanent ban
                • Account restrictions
                • Police referral (serious cases)
                
                **EMERGENCY SITUATIONS:**
                
                **Immediate Danger:**
                • Call 911 or local police
                • Go to safe location
                • Contact trusted person
                
                **Financial Scam:**
                • Contact your bank/GCash
                • File police report (for large amounts)
                • Document everything
                
                **PREVENTION TIPS:**
                
                **Before Trading:**
                • Verify user profile
                • Check ratings & history
                • Use safe payment methods
                • Meet in public places
                
                **Red Flags to Watch:**
                🚩 New account with no ratings
                🚩 Pressure for immediate payment
                🚩 Refusal to meet in person
                🚩 Prices too good to be true
                🚩 Poor/no communication
                
                **COMMON SCAMS TO KNOW:**
                
                **Shipping Scam:**
                • "Pay shipping first"
                • Fake tracking numbers
                • Empty packages
                
                **Verification Scam:**
                • "Need verification payment"
                • "Send code to confirm"
                • "Processing fee required"
                
                **Overpayment Scam:**
                • "Accidentally sent too much"
                • "Please refund difference"
                • Fake payment screenshots
                
                **YOUR SAFETY RESPONSIBILITIES:**
                • Report suspicious activity
                • Warn others about scammers
                • Follow safety guidelines
                • Help maintain community trust
                
                **CONFIDENTIALITY:**
                • Your identity protected
                • Report details confidential
                • Only necessary info shared
                • Legal compliance maintained
                
                **Remember:** Your reports help keep BarterHub safe for everyone! 🛡️
            """.trimIndent(),
            quickReplies = listOf("Report user", "Gather evidence", "Contact support", "Safety tips", "Scam examples"),
            intent = BotIntent.REPORT
        )
    }

    // Add other handlers for new intents (LOCATION, PRICE, NEGOTIATION, etc.)
    private fun handleLocation(): BotResponse {
        return BotResponse(
            message = """
                📍 **LOCATION & MEETUP GUIDE:**
                
                **SAFE MEETUP LOCATIONS:**
                
                **MALLS (Recommended):**
                • SM Malls - Food courts, information booth areas
                • Ayala Malls - Designated meetup spots
                • Robinsons Malls - Customer service areas
                • Festive Walk Malls - Public seating areas
                
                **COFFEE SHOPS:**
                • Starbucks - Always crowded, cameras
                • Coffee Bean & Tea Leaf - Well-lit, public
                • Bo's Coffee - Local, usually safe
                • McCafe - McDonald's coffee areas
                
                **PUBLIC AREAS:**
                • Police Stations (with CCTV areas)
                • Barangay Halls (during office hours)
                • Bank Lobbies (security present)
                • Fast Food Chains (Jollibee, McDonalds)
                
                **UNSAFE LOCATIONS (AVOID):**
                ❌ Private residences/homes
                ❌ Dark alleys/parking areas
                ❌ Isolated parks at night
                ❌ Hotel rooms/private spaces
                
                **LOCATION SETTING TIPS:**
                
                **For Listings:**
                • Set general area (Barangay/Municipality)
                • Not exact address (privacy)
                • Update if you move
                • Be honest about location
                
                **For Meetups:**
                • Choose midpoint when possible
                • Consider traffic/time
                • Public transportation access
                • Parking availability
                
                **MEETUP SAFETY PROTOCOL:**
                
                1. **BEFORE MEETING:**
                   • Share meetup plan with friend/family
                   • Share live location (optional)
                   • Confirm exact spot (e.g., "SM Food Court, Table near Jollibee")
                   • Agree on identification method
                
                2. **DURING MEETUP:**
                   • Arrive slightly early to scout
                   • Sit facing entrance
                   • Keep belongings secure
                   • Have phone accessible
                
                3. **AFTER MEETUP:**
                   • Inform your contact you're safe
                   • Leave location safely
                   • Don't lead to your home
                   • Rate transaction promptly
                
                **METRO MANILA SAFE SPOTS:**
                • Quezon City: Trinoma Food Court
                • Manila: SM Manila Food Court
                • Makati: Glorietta Activity Center
                • Pasig: Estancia Food Hall
                • Taguig: Market! Market! Food Area
                
                **PROVINCE MEETUP TIPS:**
                • Town plazas (during daytime)
                • Public markets (crowded areas)
                • Municipal halls
                • Church grounds (Sunday best)
                
                **WEATHER CONSIDERATIONS:**
                • Rainy season: Covered areas
                • Summer: Air-conditioned spots
                • Check traffic advisories
                • Have backup indoor location
                
                **TRANSPORTATION TIPS:**
                • Park in secure parking lots
                • Public transpo: Well-lit stations
                • Grab/Angkas: Share trip details
                • Have fare ready for quick exit
                
                **EMERGENCY PREPARATION:**
                • Save local police number
                • Know nearest hospital
                • Have emergency contact ready
                • Basic self-defense awareness
                
                **Remember:** Better safe than sorry! Choose locations wisely. 🏙️
            """.trimIndent(),
            quickReplies = listOf("Safe locations", "Meetup safety", "Public transport", "Emergency prep", "Weather tips"),
            intent = BotIntent.LOCATION
        )
    }

    private fun handlePricing(): BotResponse {
        return BotResponse(
            message = """
                💰 **PRICING & VALUATION GUIDE:**
                
                **HOW TO PRICE ITEMS FAIRLY:**
                
                **FACTORS TO CONSIDER:**
                1. **ORIGINAL PRICE** - How much was it new?
                2. **AGE** - How old is it?
                3. **CONDITION** - New, Used, For Parts?
                4. **BRAND** - Premium or generic?
                5. **MARKET DEMAND** - Popular or niche?
                6. **SEASONALITY** - In-season or off-season?
                7. **ACCESSORIES** - Complete set or bare?
                8. **WARRANTY** - Still under warranty?
                
                **CONDITION-BASED PRICING:**
                
                **Brand New (Sealed/Unused):**
                • 80-100% of original price
                • Example: ₱10,000 new → ₱8,000-₱10,000
                
                **Like New (Minimal Use):**
                • 60-80% of original price  
                • Example: ₱10,000 → ₱6,000-₱8,000
                
                **Good Condition (Used but Works):**
                • 40-60% of original price
                • Example: ₱10,000 → ₱4,000-₱6,000
                
                **Fair Condition (Visible Wear):**
                • 20-40% of original price
                • Example: ₱10,000 → ₱2,000-₱4,000
                
                **For Parts/Repair:**
                • 10-30% of original price
                • Example: ₱10,000 → ₱1,000-₱3,000
                
                **CATEGORY-SPECIFIC PRICING:**
                
                **Electronics (Fast Depreciation):**
                • Phones: 50% loss in first year
                • Laptops: 40% loss in first year
                • TVs: 30% loss in first year
                
                **Furniture (Slower Depreciation):**
                • Wood furniture: Holds value better
                • Sofas: 30-50% of original
                • Beds: 40-60% of original
                
                **Clothing (Brand Dependent):**
                • Designer: 50-70% of original
                • Fast fashion: 20-40% of original
                • Vintage: Could appreciate
                
                **PRICING STRATEGIES:**
                
                **Competitive Pricing:**
                • Check similar listings
                • Price slightly lower
                • Faster sale potential
                
                **Value Pricing:**
                • Emphasize item value
                • Bundle with accessories
                • Include free delivery
                
                **Negotiation Buffer:**
                • Add 10-20% for tawad
                • Example: Want ₱1,000 → List ₱1,200
                • Gives room for discount
                
                **PSYCHOLOGICAL PRICING:**
                • ₱999 instead of ₱1,000
                • ₱4,950 instead of ₱5,000
                • Odd pricing works better
                
                **PRICING MISTAKES TO AVOID:**
                
                ❌ **Overpricing:**
                   • Item sits for months
                   • No inquiries
                   • Eventually need big discount
                
                ❌ **Underpricing:**
                   • Sells too fast
                   • Lost profit
                   • Buyer suspicion
                
                ❌ **Emotional Pricing:**
                   • "I paid a lot for this"
                   • Sentimental value
                   • Not market-based
                
                **PRICE ADJUSTMENT TIPS:**
                • Start slightly higher
                • Reduce 10% every 2 weeks
                • Consider season/events
                • Bundle for better value
                
                **NEGOTIATION PREPARATION:**
                • Know your lowest acceptable price
                • Have reasons for your price
                • Be ready to justify value
                • Consider trade options
                
                **PRICING TOOLS:**
                • BarterHub price check feature
                • Facebook Marketplace comparison
                • Carousell price references
                • Google search for original prices
                
                **SPECIAL PRICING SITUATIONS:**
                
                **Urgent Sale:**
                • Price 20-30% below market
                • Emphasize "RFS: Need funds"
                • Quick response required
                
                **Bundle Deals:**
                • 2 items: 10% discount
                • 3+ items: 20% discount
                • Clear savings shown
                
                **Free Delivery Included:**
                • Add delivery cost to price
                • Market as "All-in price"
                • Convenience factor
                
                **Remember:** Fair price = Happy buyer + Happy seller! ⚖️
            """.trimIndent(),
            quickReplies = listOf("Price calculator", "Condition guide", "Negotiation buffer", "Market research", "Bundle pricing"),
            intent = BotIntent.PRICE
        )
    }

    private fun handleNegotiation(): BotResponse {
        return BotResponse(
            message = """
                🤝 **FILIPINO NEGOTIATION GUIDE:**
                
                **CULTURAL ETIQUETTE:**
                
                **POLITE OPENINGS:**
                • "Magandang araw po!"
                • "Interesado po ako sa item nyo"
                • "Pwede po bang magtanong?"
                • "Okay lang po bang mag-ask about price?"
                
                **RESPECTFUL LANGUAGE:**
                • Use "po" and "opo"
                • "Salamat po" frequently
                • "Pakiusap" for requests
                • "Sensya na po" for counter-offers
                
                **NEGOTIATION PROCESS:**
                
                **STEP 1: BUILD RAPPORT**
                • Compliment the item
                • Show genuine interest
                • Share common ground
                • Be friendly but professional
                
                **STEP 2: INITIAL INQUIRY**
                • "Pwede po bang malaman last price?"
                • "Negotiable po ba?"
                • "May discount po ba for cash?"
                • "Package deal po possible?"
                
                **STEP 3: MAKE OFFER**
                • Start 20-30% below asking
                • Justify your offer
                • "Kaya po ba PXXX?"
                • "PXXX po kaya?"
                
                **STEP 4: COUNTER-NEGOTIATION**
                • Meet halfway if possible
                • "PXXX po meet halfway?"
                • Add value instead of just lower price
                • "PXXX po pero ako na pickup"
                
                **STEP 5: CLOSE DEAL**
                • Agree on final price
                • Confirm meetup details
                • "Deal po! Salamat!"
                • "Sige po, kuha ko na"
                
                **NEGOTIATION TACTICS:**
                
                **For Buyers:**
                • Cash payment discount request
                • Quick transaction incentive
                • Package deal offer
                • "Last price" inquiry
                
                **For Sellers:**
                • Firm but polite on price
                • Highlight item value
                • Bundle options
                • "Meet halfway" compromise
                
                **FILIPINO NEGOTIATION PHRASES:**
                
                **Buyer Phrases:**
                • "Pwede po pa-tawad?"
                • "Last price po?"
                • "Cash po, discount po?"
                • "Kuha ko na po ngayon, pwedeng PXXX?"
                
                **Seller Responses:**
                • "Firm price po ito eh"
                • "PXXX po last price"
                • "Package deal na lang po"
                • "Meet halfway po tayo, PXXX"
                
                **NEGOTIATION DO's:**
                ✅ Be polite and respectful
                ✅ Justify your offers
                ✅ Listen to other party
                ✅ Be flexible within reason
                ✅ Know when to walk away
                
                **NEGOTIATION DON'Ts:**
                ❌ Lowball aggressively
                ❌ Pressure tactics
                ❌ Rude language
                ❌ Ghosting after agreement
                ❌ Last-minute changes
                
                **COMMON SCENARIOS:**
                
                **Item Priced Too High:**
                • "Pasensya na po, medyo mataas po"
                • "Market price po kasi ng ganito ay PXXX"
                • "Pwede po bang PXXX?"
                
                **Seller Firm on Price:**
                • "Okay po, salamat sa time"
                • "Babalikan ko po kayo kung di mabili"
                • "Pwede po trade?"
                
                **Multiple Item Discount:**
                • "Kung dalawa kukunin ko, pwedeng PXXX each?"
                • "Package deal po, PXXX for both?"
                
                **CASH DISCOUNT TIPS:**
                • "Cash po ako, pwedeng PXXX?"
                • "Bawas na po yung GCash fee"
                • "Walang installment po"
                
                **NEGOTIATION PSYCHOLOGY:**
                • Anchor high (sellers) or low (buyers)
                • Reciprocity principle
                • Scarcity effect
                • Social proof (others interested)
                
                **WHEN TO WALK AWAY:**
                • Price way above market
                • Seller/buyer unreasonable
                • Gut feeling says no
                • Better deal available
                
                **AFTER NEGOTIATION:**
                • Confirm agreement in chat
                • Set meetup immediately
                • Thank the other party
                • Honor your agreement
                
                **Remember:** Good negotiation = Both parties happy! Win-win deal ang goal! 😊
            """.trimIndent(),
            quickReplies = listOf("Polite phrases", "Price anchors", "Cash discount", "Walk away signs", "Deal closing"),
            intent = BotIntent.NEGOTIATION
        )
    }

    private fun handleHelp(): BotResponse {
        return BotResponse(
            message = """
            ❓ **HOW CAN I HELP YOU?**
            
            Here are the main topics I can assist with:
            
            1️⃣ **TRADING:**
               • Buying items
               • Selling items  
               • Trade negotiations
               • Pricing guidance
            
            2️⃣ **ACCOUNT:**
               • Profile settings
               • Security features
               • Privacy controls
               • Notification preferences
            
            3️⃣ **SAFETY:**
               • Safe trading guidelines
               • Scam prevention
               • Report procedures
               • Emergency contacts
            
            4️⃣ **FEATURES:**
               • App functionality
               • Wallet & coins
               • Categories & search
               • Rating system
            
            5️⃣ **SUPPORT:**
               • Contact options
               • Issue resolution
               • Feedback submission
               • App improvements
            
            Just tell me what you need help with! You can also use the quick reply buttons below for common topics. 😊
            
            **Remember:** I'm here 24/7 to assist you with anything BarterHub-related!
        """.trimIndent(),
            quickReplies = listOf("Trading help", "Account issues", "Safety concerns", "Contact human support", "Feature guide"),
            intent = BotIntent.HELP
        )
    }

    // Add remaining handlers (shipping, meetup, refund, verification, etc.)
    private fun handleItemCondition(): BotResponse {
        return BotResponse(
            message = """
                📊 **ITEM CONDITION GUIDE:**
                
                **CONDITION GRADING SYSTEM:**
                
                **Brand New:**
                • Original packaging
                • Never used/opened
                • All accessories included
                • Warranty intact
                • Tags/labels attached
                
                **Like New/Open Box:**
                • Used once or twice
                • No visible wear
                • Functions perfectly
                • All accessories present
                • May not have box
                
                **Good Condition:**
                • Lightly used
                • Minor cosmetic wear
                • Functions 100%
                • May have small scratches
                • Complete accessories
                
                **Fair Condition:**
                • Moderately used
                • Visible wear/tear
                • Functions properly
                • May need minor repair
                • Some accessories missing
                
                **Poor Condition/For Parts:**
                • Heavily used
                • Significant damage
                • May not function fully
                • For repair or parts only
                • Sold "as-is"
                
                **DESCRIBING CONDITION HONESTLY:**
                
                **Do's:**
                ✅ Take photos of all flaws
                ✅ Mention wear in description
                ✅ Test all functions
                ✅ Note missing accessories
                ✅ Be specific about issues
                
                **Don'ts:**
                ❌ Hide defects in photos
                ❌ Use vague terms only
                ❌ Claim "like new" when used
                ❌ Forget to mention repairs
                ❌ Overstate condition
                
                **COMMON ITEM-SPECIFIC CONDITIONS:**
                
                **Electronics:**
                • Battery health percentage
                • Screen scratches (minor/major)
                • Button/port functionality
                • Software issues
                • Repair history
                
                **Clothing:**
                • Stains (removable/permanent)
                • Tears/rips
                • Fading/discoloration
                • Fit/sizing accuracy
                • Odors
                
                **Furniture:**
                • Structural integrity
                • Surface scratches
                • Stains/upholstery wear
                • Missing parts
                • Assembly required
                
                **Vehicles/Bikes:**
                • Mileage/usage
                • Maintenance history
                • Accident history
                • Rust/corrosion
                • Tire wear
                
                **TESTING BEFORE POSTING:**
                
                **Electronics Testing:**
                1. Power on/off
                2. All buttons/ports
                3. Screen display
                4. Sound output
                5. Camera functions
                6. Battery charging
                
                **Furniture Testing:**
                1. Weight capacity
                2. Stability check
                3. Drawer/shelf function
                4. Surface condition
                5. Assembly completeness
                
                **CONDITION DISCLOSURE:**
                • "Minor scratch on back"
                • "Battery lasts 4 hours"
                • "Missing charger, works with any USB-C"
                • "Small stain on sleeve (see photo)"
                • "Repaired screen, works perfectly"
                
                **PHOTO REQUIREMENTS:**
                • All angles (front, back, sides)
                • Close-ups of flaws
                • Function demonstration
                • Size comparison
                • Accessories shown
                
                **PRICING BY CONDITION:**
                • Brand New: 80-100%
                • Like New: 60-80%
                • Good: 40-60%
                • Fair: 20-40%
                • Poor: 10-30%
                
                **BUYER'S CHECKLIST:**
                1. Ask about specific concerns
                2. Request additional photos
                3. Inquire about repair history
                4. Test during meetup
                5. Compare with description
                
                **SELLER'S PROTECTION:**
                1. Document condition thoroughly
                2. Take video before packing
                3. Note "as-is" if unsure
                4. Be honest to avoid returns
                5. Price according to condition
                
                **Remember:** Honest condition description = Trustworthy seller reputation! 🏆
            """.trimIndent(),
            quickReplies = listOf("Condition grades", "Testing guide", "Photo requirements", "Pricing by condition", "Disclosure tips"),
            intent = BotIntent.ITEM_CONDITION
        )
    }

    private fun handleShipping(): BotResponse {
        return BotResponse(
            message = """
                🚚 **SHIPPING & DELIVERY GUIDE:**
                
                **COURIER OPTIONS:**
                
                **LOCAL COURIERS:**
                
                **LBC Express (Recommended):**
                • Nationwide coverage
                • COD available
                • Tracking system
                • Insurance options
                • 2-5 days delivery
                
                **J&T Express:**
                • Affordable rates
                • COD available
                • Good urban coverage
                • 1-3 days delivery
                
                **2Go Transport:**
                • For heavy/bulky items
                • Pallet shipping
                • Vehicle transport
                • 3-7 days delivery
                
                **SAME-DAY DELIVERY:**
                • Grab Express
                • Lalamove
                • Angkas Padala
                • JoyRide Send
                • Within city only
                
                **INTERNATIONAL (If Allowed):**
                • DHL/FedEx
                • EMS Philpost
                • ShippingCart
                • Johnny Air
                
                **SHIPPING COSTS:**
                
                **Factors Affecting Cost:**
                1. Package size/weight
                2. Destination distance
                3. Delivery speed
                4. Insurance value
                5. Special handling
                
                **Sample Rates (Manila to Province):**
                • Small package (1kg): ₱100-₱150
                • Medium (3kg): ₱150-₱250
                • Large (5kg): ₱250-₱400
                • Bulky items: ₱400+
                
                **PACKAGING GUIDE:**
                
                **For Fragile Items:**
                • Bubble wrap (multiple layers)
                • Cardboard box (double wall)
                • "Fragile" stickers
                • Fill empty spaces
                • Waterproof outer layer
                
                **For Electronics:**
                • Anti-static bubble wrap
                • Original box if available
                • Remove batteries
                • Secure cables separately
                • Include manuals
                
                **For Clothing:**
                • Vacuum seal (optional)
                • Plastic protection
                • Fold neatly
                • Include hanger if delicate
                
                **SHIPPING PROCESS:**
                
                **STEP 1: PACKAGE PREPARATION**
                • Clean item thoroughly
                • Take pre-shipping photos
                • Package securely
                • Label clearly
                
                **STEP 2: DOCUMENTATION**
                • Fill out waybill accurately
                • Declare actual value
                • Choose insurance
                • Get receipt
                
                **STEP 3: DROP-OFF/PICKUP**
                • Courier branch or pickup
                • Verify waybill details
                • Keep tracking number
                • Take photo of packed item
                
                **STEP 4: TRACKING**
                • Share tracking with buyer
                • Monitor delivery status
                • Update buyer regularly
                • Follow up if delayed
                
                **COD (CASH ON DELIVERY):**
                
                **For Sellers:**
                • Higher fees (5-10%)
                • Payment after delivery
                • Risk of buyer refusal
                • Courier holds payment 1-2 weeks
                
                **For Buyers:**
                • Pay upon receipt
                • Inspect before paying
                • Have exact amount ready
                • Get official receipt
                
                **INSURANCE:**
                
                **When to Insure:**
                • Valuable items (₱5,000+)
                • Fragile items
                • Long-distance shipping
                • No original packaging
                
                **Insurance Costs:**
                • 1-2% of declared value
                • Minimum ₱50
                • Maximum depends on courier
                
                **Claim Process:**
                1. Report damage immediately
                2. Provide photos/video
                3. File claim within 24 hours
                4. Wait for assessment
                5. Receive compensation
                
                **SHIPPING TIPS:**
                
                **For Better Experience:**
                • Ship early in week (avoid weekend delays)
                • Include thank you note
                • Add free small gift (optional)
                • Follow up after delivery
                
                **Red Flags:**
                🚩 Courier asking for payment upfront
                🚩 No tracking number provided
                🚩 Unusually cheap shipping
                🚩 Pressure to skip insurance
                
                **INTERNATIONAL SHIPPING:**
                • Check customs regulations
                • Proper documentation
                • May take 1-4 weeks
                • Higher costs/fees
                
                **ENVIRONMENTAL TIPS:**
                • Reuse packaging materials
                • Use biodegradable materials
                • Optimize package size
                • Combine shipments if possible
                
                **Remember:** Good packaging + Reliable courier = Happy buyer! 📦
            """.trimIndent(),
            quickReplies = listOf("Courier rates", "Packaging guide", "COD tips", "Insurance info", "Tracking help"),
            intent = BotIntent.SHIPPING
        )
    }

    private fun handleMeetup(): BotResponse {
        return BotResponse(
            message = """
                🤝 **MEETUP & TRANSACTION GUIDE:**
                
                **BEFORE MEETUP PREPARATION:**
                
                **ESSENTIAL CHECKS:**
                1. **Verify Identity**
                   • Ask for recent photo with item
                   • Confirm contact number
                   • Agree on identification method
                
                2. **Confirm Details**
                   • Final price agreed
                   • Exact meetup location
                   • Date and time
                   • Rain/weather plan
                
                3. **Safety Measures**
                   • Share meetup plan with friend
                   • Save emergency contacts
                   • Charge phone fully
                   • Bring power bank
                
                **WHAT TO BRING:**
                
                **For Sellers:**
                • Item (cleaned and prepared)
                • All accessories
                • Receipt/agreement form
                • Pen for signing
                • Change (if needed)
                • Packaging materials (if delivery)
                
                **For Buyers:**
                • Exact payment amount
                • Payment method confirmed
                • Testing equipment (if needed)
                • Friend/companion
                • Shopping bag for item
                
                **MEETUP LOCATION CHECKLIST:**
                
                **Ideal Location Features:**
                ✅ Well-lit area
                ✅ Security cameras
                ✅ Security personnel
                ✅ Public restrooms
                ✅ Parking available
                ✅ Public transportation access
                ✅ Seating available
                ✅ Weather protection
                
                **TIMING CONSIDERATIONS:**
                • Avoid rush hours (7-9AM, 5-7PM)
                • Daylight hours recommended
                • Consider store/bank hours
                • Allow extra time for traffic
                
                **MEETUP PROTOCOL:**
                
                **ARRIVAL:**
                • Arrive 10-15 minutes early
                • Park in visible area
                • Sit facing entrance
                • Keep phone accessible
                
                **GREETING:**
                • Polite introduction
                • Confirm identity
                • Small talk to build rapport
                • Thank for coming
                
                **INSPECTION:**
                • Allow thorough inspection
                • Demonstrate functions
                • Answer questions honestly
                • Be patient with testing
                
                **TRANSACTION:**
                • Count money carefully together
                • Check for counterfeit bills
                • Get receipt/agreement signed
                • Exchange items carefully
                
                **COMMON SCENARIOS:**
                
                **Buyer Brings Less Money:**
                • Politely remind agreed price
                • Offer to reschedule
                • Partial payment with collateral
                • Walk away if needed
                
                **Item Not as Described:**
                • Stay calm and discuss
                • Point out discrepancies
                • Renegotiate price
                • Or cancel transaction politely
                
                **No Show/Late Arrival:**
                • Wait 15-30 minutes max
                • Message/call to check
                • Leave if no response
                • Report habitual no-shows
                
                **WEATHER CONSIDERATIONS:**
                • Check forecast day before
                • Have indoor backup location
                • Bring umbrella/raincoat
                • Reschedule if severe weather
                
                **AFTER MEETUP:**
                1. **Safety First**
                   • Leave location safely
                   • Don't lead to your home
                   • Inform contact you're safe
                
                2. **Documentation**
                   • Take photo of item with buyer/seller
                   • Save receipt/agreement
                   • Update listing status
                
                3. **Feedback**
                   • Rate within 24 hours
                   • Leave honest feedback
                   • Report any issues
                
                **EMERGENCY PROCEDURES:**
                
                **Feeling Unsafe:**
                1. Excuse yourself politely
                2. Move to crowded area
                3. Call friend/family
                4. Contact security/police if needed
                
                **Threats/Intimidation:**
                1. Stay calm
                2. Don't escalate
                3. Leave immediately
                4. Report to authorities
                
                **Item Stolen During Meetup:**
                1. Note description of person
                2. Contact security immediately
                3. File police report
                4. Report on BarterHub
                
                **SPECIAL MEETUPS:**
                
                **High-Value Items (₱10,000+):**
                • Police station meetup recommended
                • Bring friend/family
                • Bank lobby transaction
                • Escrow service consideration
                
                **Vehicle Transactions:**
                • LTO office for transfer
                • Bring mechanic friend
                • Test drive in safe area
                • Complete paperwork
                
                **Pet Transactions:**
                • Vet clinic meetup
                • Health check together
                • Bring carrier
                • Complete vaccination records
                
                **Remember:** Successful meetup = Preparation + Safety + Politeness! 🎯
            """.trimIndent(),
            quickReplies = listOf("Location checklist", "What to bring", "Safety protocol", "Weather plans", "Emergency procedures"),
            intent = BotIntent.MEETUP
        )
    }

    // Continue adding other handlers as needed...

    private fun handleUnknown(input: String): BotResponse {
        // Try to give more helpful responses based on keywords
        val lower = input.lowercase()

        val suggestions = when {
            lower.contains("phone") || lower.contains("iphone") || lower.contains("samsung") ->
                listOf("Sell phone", "Buy phone", "Phone pricing", "Phone condition")

            lower.contains("laptop") || lower.contains("computer") ->
                listOf("Sell laptop", "Buy laptop", "Laptop specs", "Laptop pricing")

            lower.contains("clothes") || lower.contains("dress") || lower.contains("shirt") ->
                listOf("Sell clothes", "Buy clothes", "Clothing sizes", "Fashion tips")

            lower.contains("car") || lower.contains("motor") || lower.contains("vehicle") ->
                listOf("Sell vehicle", "Buy vehicle", "Vehicle papers", "Test drive")

            lower.contains("house") || lower.contains("apartment") || lower.contains("rent") ->
                listOf("Property rules", "Rental guidelines", "Real estate", "Location help")

            else -> listOf("Selling guide", "Buying tips", "Account help", "Safety info", "Contact support")
        }

        return BotResponse(
            message = """
                🤖 **Naiintindihan ko!** (I understand!)
                
                Hindi ko masyadong nakuha ang tanong mo, pero nandito ako para tulungan ka!
                
                **Pwede mong subukan:**
                • Magtanong sa Tagalog o English
                • Gamitin ang quick reply buttons
                • Magtanong ng mas specific
                
                **Common na mga tanong:**
                • "Paano magbenta ng cellphone?"
                • "Magkano ang fair price ng laptop?"
                • "Safe ba mag-meet sa SM?"
                • "Paano mag-negotiate ng tawad?"
                
                **O kaya pumili sa mga options sa baba!** 👇
                
                _Ako si Barti, nandito para sa'yo!_ 😊
            """.trimIndent(),
            quickReplies = suggestions,
            intent = BotIntent.UNKNOWN
        )
    }

    private fun handleRefund(): BotResponse {
        return topicResponse(
            intent = BotIntent.REFUND,
            title = "Refund and Return Help",
            body = "For refunds or returns, keep the chat, photos, receipts, and meetup/payment proof. Contact support if a trade partner will not resolve the issue.",
            quickReplies = listOf("Report issue", "Contact support", "Safety tips", "Trade help"),
            action = BotAction.OPEN_SUPPORT
        )
    }

    private fun handleVerification(): BotResponse {
        return topicResponse(
            intent = BotIntent.VERIFICATION,
            title = "Verification Help",
            body = "Use profile verification to build trust. Upload clear ID photos only through the verification screen and wait for admin review.",
            quickReplies = listOf("Open profile", "ID verification", "Safety tips", "Contact support"),
            action = BotAction.OPEN_PROFILE
        )
    }

    private fun handleFeedback(): BotResponse {
        return topicResponse(
            intent = BotIntent.FEEDBACK,
            title = "Feedback Help",
            body = "After a trade, leave honest feedback about item accuracy, communication, meetup timing, and overall experience.",
            quickReplies = listOf("Rating guide", "Report issue", "Contact support", "Trade help")
        )
    }

    private fun handleTradeHistory(): BotResponse {
        return topicResponse(
            intent = BotIntent.TRADE_HISTORY,
            title = "Trade History",
            body = "Your past trades and receipts help you review completed swaps, ratings, and transaction details.",
            quickReplies = listOf("Open wallet", "Receipts", "Rating guide", "Trade help"),
            action = BotAction.OPEN_WALLET
        )
    }

    private fun handleFavorites(): BotResponse {
        return topicResponse(
            intent = BotIntent.FAVORITES,
            title = "Favorites and Saved Items",
            body = "Save items you like so you can compare them later, revisit sellers, and continue trade conversations faster.",
            quickReplies = listOf("Search items", "Buying tips", "Categories", "Trade help"),
            action = BotAction.OPEN_SEARCH
        )
    }

    private fun handleNotifications(): BotResponse {
        return topicResponse(
            intent = BotIntent.NOTIFICATIONS,
            title = "Notifications",
            body = "Notifications help you catch new messages, trade updates, ratings, and account alerts. Check app and phone notification settings if alerts are missing.",
            quickReplies = listOf("Account settings", "Message alerts", "Trade updates", "Contact support"),
            action = BotAction.OPEN_PROFILE
        )
    }

    private fun handleLanguage(): BotResponse {
        return topicResponse(
            intent = BotIntent.LANGUAGE,
            title = "Language Help",
            body = "You can ask me in English, Tagalog, or mixed Taglish. I will do my best to answer clearly.",
            quickReplies = listOf("English help", "Tagalog help", "Trading help", "Account help")
        )
    }

    private fun handleTerms(): BotResponse {
        return topicResponse(
            intent = BotIntent.TERMS,
            title = "Terms and Rules",
            body = "Follow BarterHub rules: trade honestly, avoid prohibited items, respect other users, and keep proof of every transaction.",
            quickReplies = listOf("Safety tips", "Report issue", "Privacy", "Contact support"),
            action = BotAction.OPEN_SAFETY_GUIDE
        )
    }

    private fun handlePrivacy(): BotResponse {
        return topicResponse(
            intent = BotIntent.PRIVACY,
            title = "Privacy Help",
            body = "Protect personal data by sharing only what is needed for the trade. Avoid posting sensitive IDs, passwords, or private payment details in chat.",
            quickReplies = listOf("Account settings", "Safety tips", "Report issue", "Contact support"),
            action = BotAction.OPEN_PROFILE
        )
    }

    private fun handleAppFeedback(): BotResponse {
        return topicResponse(
            intent = BotIntent.APP_FEEDBACK,
            title = "App Feedback",
            body = "Suggestions help improve BarterHub. Send clear details about the screen, problem, or feature idea so support can review it.",
            quickReplies = listOf("Contact support", "Report issue", "Feature idea", "Help"),
            action = BotAction.OPEN_SUPPORT
        )
    }

    private fun handlePromotions(): BotResponse {
        return topicResponse(
            intent = BotIntent.PROMOTIONS,
            title = "Promotions",
            body = "Check announcements and notifications for active promos, rewards, boosts, and limited-time offers.",
            quickReplies = listOf("Notifications", "Wallet", "How to earn", "Referral"),
            action = BotAction.OPEN_WALLET
        )
    }

    private fun handleReferral(): BotResponse {
        return topicResponse(
            intent = BotIntent.REFERRAL,
            title = "Referral Help",
            body = "Invite trusted friends to BarterHub and follow the referral instructions in the app to qualify for rewards.",
            quickReplies = listOf("How to earn", "Wallet", "Invite friends", "Contact support"),
            action = BotAction.OPEN_WALLET
        )
    }

    private fun handleHowToEarn(): BotResponse {
        return topicResponse(
            intent = BotIntent.HOW_TO_EARN,
            title = "How to Earn",
            body = "Earn by completing trades, keeping good ratings, joining eligible rewards, and inviting friends when referral rewards are available.",
            quickReplies = listOf("Open wallet", "Referral", "Trade tips", "Promotions"),
            action = BotAction.OPEN_WALLET
        )
    }

    private fun topicResponse(
        intent: BotIntent,
        title: String,
        body: String,
        quickReplies: List<String>,
        action: BotAction? = null
    ): BotResponse {
        return BotResponse(
            message = "**$title**\n\n$body",
            quickReplies = quickReplies,
            action = action,
            intent = intent
        )
    }
}

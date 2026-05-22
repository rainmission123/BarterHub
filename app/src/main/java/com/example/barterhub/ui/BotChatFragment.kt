package com.example.barterhub.ui

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.lottie.LottieAnimationView
import com.example.barterhub.R
import com.example.barterhub.adapters.BotMessageAdapter
import com.example.barterhub.bot.BotEngine
import com.example.barterhub.data.models.bot.BotAction
import com.example.barterhub.databinding.FragmentBotChatBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BotChatFragment : Fragment() {
    private var _binding: FragmentBotChatBinding? = null
    private val binding get() = _binding!!
    private val messages = mutableListOf<Pair<String, Boolean>>()
    private lateinit var adapter: BotMessageAdapter
    private lateinit var typingLottie: LottieAnimationView

    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): android.view.View {
        _binding = FragmentBotChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupListeners()
        sendInitialMessage()
    }

    private fun setupUI() {
        adapter = BotMessageAdapter(messages)
        binding.botRecyclerView.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.botRecyclerView.adapter = adapter
        typingLottie = binding.typingLottie
        typingLottie.visibility = View.GONE
    }

    private fun handleBotAction(action: BotAction?) {
        when (action) {
            is BotAction.OPEN_ADD_ITEM -> {
                // Use the bot-specific action
                findNavController().navigate(R.id.action_bot_to_add_item)
            }

            is BotAction.OPEN_WALLET -> {
                // Use the bot-specific action
                findNavController().navigate(R.id.action_bot_to_wallet)
            }

            is BotAction.OPEN_PROFILE -> {
                // Use the bot-specific action
                findNavController().navigate(R.id.action_bot_to_profile)
            }

            is BotAction.OPEN_SUPPORT -> {
                // Use the bot-specific action
                findNavController().navigate(R.id.action_bot_to_support)
            }

            is BotAction.OPEN_SEARCH -> {
                // Use the bot-specific action
                findNavController().navigate(R.id.action_bot_to_search)
            }

            is BotAction.OPEN_CATEGORIES -> {
                // Use the bot-specific action
                findNavController().navigate(R.id.action_bot_to_categories)
            }

            is BotAction.OPEN_SAFETY_GUIDE -> {
                // Use the bot-specific action
                findNavController().navigate(R.id.action_bot_to_safety)
            }

            is BotAction.OPEN_CHAT_SUPPORT -> {
                // Use the bot-specific action
                findNavController().navigate(R.id.action_bot_to_live_support)
            }

            is BotAction.OPEN_URL -> {
                // Show toast for URL
                showToast("Opening: ${action.url}")
            }

            is BotAction.OPEN_SCREEN -> {
                when (action.screenName) {
                    "TradeHistory" -> {
                        // For TradeHistory, navigate to profile then to trade history
                        findNavController().navigate(R.id.action_bot_to_profile)
                        // Note: This won't work directly, need to handle differently
                        showToast("Go to Profile → Trade History")
                    }
                    "Favorites" -> {
                        showToast("Go to Profile → Favorites")
                    }
                    "MyListings" -> {
                        showToast("Go to Profile → My Listings")
                    }
                    "Settings" -> {
                        showToast("Go to Menu → Settings")
                    }
                    else -> {
                        showToast("Opening: ${action.screenName}")
                    }
                }
            }

            null -> {
                // No action needed
            }
        }
    }

    private fun setupListeners() {
        binding.sendButton.setOnClickListener {
            handleUserMessage()
        }

        // Enable send on enter key
        binding.messageEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                handleUserMessage()
                true
            } else {
                false
            }
        }
    }

    private fun handleUserMessage() {
        val text = binding.messageEditText.text.toString().trim()
        if (text.isNotEmpty()) {
            addUserMessage(text)
            binding.messageEditText.text.clear()

            // Process through BotEngine
            val response = BotEngine.handleUserInput(text)
            sendBotMessageWithDelay(response)

            // Log analytics (optional)
            logUserMessage(text, response.intent)
        }
    }

    private fun openUrl(url: String) {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(url)
        )
        startActivity(intent)
    }


    private fun sendInitialMessage() {
        val initialMessage = arguments?.getString("BOT_INITIAL_MESSAGE") ?:
        "👋 Hi! I'm your BarterHub assistant. How can I help you today?"

        val response = BotEngine.handleUserInput(initialMessage)
        sendBotMessageWithDelay(response)
    }

    private fun addUserMessage(text: String) {
        messages.add(Pair(text, true))
        adapter.notifyItemInserted(messages.size - 1)
        binding.botRecyclerView.smoothScrollToPosition(messages.size - 1)
    }

    private fun addBotMessage(text: String) {
        messages.add(Pair(text, false))
        adapter.notifyItemInserted(messages.size - 1)
        binding.botRecyclerView.smoothScrollToPosition(messages.size - 1)
    }

    private fun sendBotMessageWithDelay(response: com.example.barterhub.data.models.bot.BotResponse, delayMillis: Long = 1200) {
        lifecycleScope.launch {
            // Show typing indicator
            typingLottie.visibility = View.VISIBLE
            typingLottie.playAnimation()

            delay(delayMillis)

            // Hide typing indicator
            typingLottie.visibility = View.GONE
            typingLottie.pauseAnimation()

            // Add bot message
            addBotMessage(response.message)

            // Show quick replies
            showQuickReplies(response.quickReplies)

            handleBotAction(response.action)

        }
    }

    private fun showQuickReplies(replies: List<String>) {
        binding.quickReplyChipGroup.removeAllViews()

        if (replies.isEmpty()) {
            binding.quickReplyChipGroup.visibility = View.GONE
            return
        }

        binding.quickReplyChipGroup.visibility = View.VISIBLE

        replies.take(5).forEach { replyText ->
            val chip = Chip(requireContext()).apply {
                text = replyText
                isClickable = true
                isCheckable = false

                // Style the chip
                chipStrokeWidth = 1f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
                setChipBackgroundColorResource(R.color.white)
                setChipStrokeColorResource(R.color.primary_color)

                // Add ripple effect
                setRippleColorResource(R.color.primary_light)

                setOnClickListener {
                    // Add visual feedback
                    alpha = 0.7f
                    postDelayed({ alpha = 1.0f }, 200)

                    // Handle click
                    addUserMessage(replyText)
                    val response = BotEngine.handleUserInput(replyText)
                    sendBotMessageWithDelay(response)
                }
            }
            binding.quickReplyChipGroup.addView(chip)
        }
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun logUserMessage(text: String, intent: com.example.barterhub.data.models.bot.BotIntent) {
        // Log to analytics (Firebase, etc.)
        // Example: FirebaseAnalytics.logEvent("bot_message", bundleOf(
        //     "message" to text,
        //     "intent" to intent.name
        // ))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
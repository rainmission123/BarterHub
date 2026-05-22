package com.example.barterhub.ui.earn

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.barterhub.databinding.FragmentReferralShareBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ReferralShareFragment : Fragment() {

    private var _binding: FragmentReferralShareBinding? = null
    private val binding get() = _binding!!

    private lateinit var referralManager: ReferralManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReferralShareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        referralManager = ReferralManager(
            context = requireContext(),
            auth = FirebaseAuth.getInstance(),
            database = FirebaseDatabase.getInstance()
        )

        loadReferralData()

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnShareNow.setOnClickListener {
            val message = binding.tvMessagePreview.text.toString()
            if (message.isBlank()) {
                Toast.makeText(requireContext(), "Referral message is not ready yet.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            performShare(message)
        }
    }

    private fun loadReferralData() {
        referralManager.loadOrCreateReferralCode(
            onSuccess = { code ->
                val message = referralManager.buildReferralMessage(code)
                binding.tvReferralCode.text = code
                binding.tvMessagePreview.text = message
            },
            onError = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                binding.tvReferralCode.text = "Unavailable"
                binding.tvMessagePreview.text = ""
            }
        )
    }

    private fun performShare(message: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            putExtra(Intent.EXTRA_SUBJECT, "Join BarterHub and Earn Coins!")
        }
        startActivity(Intent.createChooser(shareIntent, "Invite Friends to BarterHub"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
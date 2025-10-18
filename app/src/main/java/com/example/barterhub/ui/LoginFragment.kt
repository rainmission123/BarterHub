package com.example.barterhub.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.barterhub.R

class LoginFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // i-bind mo sa layout ng fragment_login.xml
        return inflater.inflate(R.layout.fragment_login, container, false)
    }
}
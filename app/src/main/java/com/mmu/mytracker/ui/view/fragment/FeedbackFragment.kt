package com.mmu.mytracker.ui.view.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.mmu.mytracker.R
import com.mmu.mytracker.data.model.Feedback

class FeedbackFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_feedback, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<ImageButton>(R.id.btnBack)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmitFeedback)
        val etName = view.findViewById<TextInputEditText>(R.id.etFeedbackName)
        val etEmail = view.findViewById<TextInputEditText>(R.id.etFeedbackEmail)
        val etContent = view.findViewById<TextInputEditText>(R.id.etFeedbackContent)

        btnBack.setOnClickListener {
            requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = R.id.nav_home
        }

        btnSubmit.setOnClickListener {
            val content = etContent.text.toString()

            if (content.isEmpty()) {
                etContent.error = "Please write something!"
                return@setOnClickListener
            }

            val feedback = Feedback(
                username = etName.text.toString(),
                email = etEmail.text.toString(),
                content = content
            )

            btnSubmit.isEnabled = false
            btnSubmit.text = "Sending..."

            FirebaseFirestore.getInstance().collection("app_feedback")
                .add(feedback)
                .addOnSuccessListener {
                    Toast.makeText(context, "Thank you for your feedback!", Toast.LENGTH_LONG).show()
                    etContent.text?.clear()
                    btnSubmit.isEnabled = true
                    btnSubmit.text = "Submit Feedback"
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to send: ${it.message}", Toast.LENGTH_SHORT).show()
                    btnSubmit.isEnabled = true
                    btnSubmit.text = "Submit Feedback"
                }
        }
    }
}
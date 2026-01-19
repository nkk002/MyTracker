package com.mmu.mytracker.ui.view.activity

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.mmu.mytracker.R
import com.mmu.mytracker.data.model.Feedback

class FeedbackActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnSubmit = findViewById<Button>(R.id.btnSubmitFeedback)
        val etName = findViewById<TextInputEditText>(R.id.etFeedbackName)
        val etEmail = findViewById<TextInputEditText>(R.id.etFeedbackEmail)
        val etContent = findViewById<TextInputEditText>(R.id.etFeedbackContent)

        btnBack.setOnClickListener {
            finish()
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
                    Toast.makeText(this, "Thank you for your feedback!", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to send: ${it.message}", Toast.LENGTH_SHORT).show()
                    btnSubmit.isEnabled = true
                    btnSubmit.text = "Submit Feedback"
                }
        }
    }
}
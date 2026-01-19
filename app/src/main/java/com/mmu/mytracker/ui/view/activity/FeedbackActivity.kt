package com.mmu.mytracker.ui.view.activity

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton // 引入 ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText // 引入新的 EditText 类型
import com.google.firebase.firestore.FirebaseFirestore
import com.mmu.mytracker.R
import com.mmu.mytracker.data.model.Feedback

class FeedbackActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        // 1. 绑定 UI 组件 (注意类型变成了 TextInputEditText)
        val btnBack = findViewById<ImageButton>(R.id.btnBack) // 返回按钮
        val btnSubmit = findViewById<Button>(R.id.btnSubmitFeedback)
        val etName = findViewById<TextInputEditText>(R.id.etFeedbackName)
        val etEmail = findViewById<TextInputEditText>(R.id.etFeedbackEmail)
        val etContent = findViewById<TextInputEditText>(R.id.etFeedbackContent)

        // 2. 设置返回按钮逻辑
        btnBack.setOnClickListener {
            finish() // 🔥 这一行代码就是 "Go Back to Homepage" 的关键
        }

        // 3. 设置提交按钮逻辑 (保持不变)
        btnSubmit.setOnClickListener {
            val content = etContent.text.toString()

            if (content.isEmpty()) {
                etContent.error = "Please write something!" // 更加好看的错误提示
                return@setOnClickListener
            }

            val feedback = Feedback(
                username = etName.text.toString(),
                email = etEmail.text.toString(),
                content = content
            )

            // Disable button to prevent double click
            btnSubmit.isEnabled = false
            btnSubmit.text = "Sending..."

            FirebaseFirestore.getInstance().collection("app_feedback")
                .add(feedback)
                .addOnSuccessListener {
                    Toast.makeText(this, "Thank you for your feedback!", Toast.LENGTH_LONG).show()
                    finish() // 提交成功后也自动退回主页
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to send: ${it.message}", Toast.LENGTH_SHORT).show()
                    btnSubmit.isEnabled = true
                    btnSubmit.text = "Submit Feedback"
                }
        }
    }
}
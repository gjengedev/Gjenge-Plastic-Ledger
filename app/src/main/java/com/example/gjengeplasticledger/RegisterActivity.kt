package com.example.gjengeplasticledger

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance() // Initialize Firestore

        val email = findViewById<EditText>(R.id.regEmail)
        val password = findViewById<EditText>(R.id.regPassword)
        val registerBtn = findViewById<Button>(R.id.registerBtn)
        val roleSpinner = findViewById<Spinner>(R.id.regRoleSpinner) // Find your spinner
        val signInRedirect = findViewById<TextView>(R.id.signInRedirect)

// Set up the spinner choices (Only Collector available for public registration)
        val roles = listOf("Collector")
        val adapter = ArrayAdapter(this, R.layout.spinner_item, roles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        roleSpinner.adapter = adapter

        registerBtn.setOnClickListener {

            val emailText = email.text.toString().trim()
            val passwordText = password.text.toString().trim()

            // ✅ VALIDATION
            if (emailText.isEmpty()) {
                email.error = "Email is required"
                return@setOnClickListener
            }

            if (passwordText.length < 6) {
                password.error = "Password must be at least 6 characters"
                return@setOnClickListener
            }

            // 🔥 Loading state (professional UI behavior)
            registerBtn.isEnabled = false
            registerBtn.text = getString(R.string.registering)

            auth.createUserWithEmailAndPassword(emailText, passwordText)
                .addOnSuccessListener { task ->
                    val uid = task.user?.uid
                    val roleText = roleSpinner.selectedItem.toString()
                    val gjengeID = "GPL-${(100000..999999).random()}"

                    // Create a map of the user data
                    val userMap = hashMapOf(
                        "email" to emailText,
                        "role" to roleText,
                        "gjengeID" to gjengeID,
                        "nationalID" to "",
                        "location" to ""
                    )

                    // Save to Firestore in a "Users" collection
                    if (uid != null) {
                        db.collection("Users").document(uid).set(userMap)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Account Created as $roleText", Toast.LENGTH_SHORT).show()

                                // Now navigate back to login
                                val intent = Intent(this, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to save user data: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                .addOnFailureListener { e ->

                    // 🔄 restore button state
                    registerBtn.isEnabled = true
                    registerBtn.text = getString(R.string.register)

                    Toast.makeText(
                        this,
                        "Registration Failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }

        signInRedirect.setOnClickListener {
            // Navigate back to the Login page (MainActivity)
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // Optional: Closes RegisterActivity so they don't go back to it
        }
    }
}
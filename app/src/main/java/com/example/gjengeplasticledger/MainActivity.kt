package com.example.gjengeplasticledger

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val spinner: Spinner = findViewById(R.id.roleSpinner)
        val roles = listOf("Collector", "Admin")
        val adapter = ArrayAdapter(this, R.layout.spinner_item, roles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val auth = FirebaseAuth.getInstance()

        val email = findViewById<EditText>(R.id.email)
        val password = findViewById<EditText>(R.id.password)
        val loginBtn = findViewById<Button>(R.id.loginBtn)
        val registerText = findViewById<TextView>(R.id.registerText)

        loginBtn.setOnClickListener {

            val emailText = email.text.toString().trim()
            val passwordText = password.text.toString().trim()

            // ✅ Validation
            if (emailText.isEmpty()) {
                email.error = "Enter email"
                return@setOnClickListener
            }

            if (passwordText.isEmpty()) {
                password.error = "Enter password"
                return@setOnClickListener
            }

            loginBtn.isEnabled = false
            loginBtn.text = "Logging in..."

            auth.signInWithEmailAndPassword(emailText, passwordText)
                .addOnSuccessListener { task ->
                    val uid = task.user?.uid
                    val selectedRole = spinner.selectedItem.toString() // What they claim to be

                    // Go get their REAL role from the database
                    FirebaseFirestore.getInstance().collection("Users").document(uid!!)
                        .get()
                        .addOnSuccessListener { document ->
                            if (document.exists()) {
                                val actualRole = document.getString("role")

                                // Only let them in if their DB role matches what they selected
                                if (actualRole == selectedRole) {
                                    val intent = if (actualRole == "Collector") {
                                        Intent(this, CollectorDashboard::class.java)
                                    } else {
                                        Intent(this, AdminDashboard::class.java)
                                    }
                                    startActivity(intent)
                                    finish()
                                } else {
                                    // SECURE: This prevents a Collector from pretending to be an Admin
                                    FirebaseAuth.getInstance().signOut()
                                    Toast.makeText(this, "Access Denied: You are not an $selectedRole", Toast.LENGTH_LONG).show()
                                    loginBtn.isEnabled = true
                                    loginBtn.text = "Login"
                                }
                            } else {
                                Toast.makeText(this, "User data not found.", Toast.LENGTH_SHORT).show()
                                loginBtn.isEnabled = true
                                loginBtn.text = "Login"
                            }
                        }
                }
                .addOnFailureListener { e ->

                    loginBtn.isEnabled = true
                    loginBtn.text = "Login"

                    Toast.makeText(
                        this,
                        "Login Failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }

        registerText.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}

package com.example.gjengeplasticledger

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var profileImageLarge: ImageView
    private lateinit var genderSpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val backBtn = findViewById<ImageButton>(R.id.backBtn)
        val profileNameLarge = findViewById<TextView>(R.id.profileNameLarge)
        val gjengeIdLarge = findViewById<TextView>(R.id.gjengeIdLarge)
        profileImageLarge = findViewById(R.id.profileImageLarge)

        val nationalIdInput = findViewById<EditText>(R.id.nationalIdInput)
        val locationInput = findViewById<EditText>(R.id.locationInput)
        genderSpinner = findViewById(R.id.genderSpinner)
        val saveProfileBtn = findViewById<Button>(R.id.saveProfileBtn)

        val btnUploadFront = findViewById<LinearLayout>(R.id.btnUploadFront)
        val btnUploadBack = findViewById<LinearLayout>(R.id.btnUploadBack)

        // Setup Gender Spinner
        val genders = listOf("Male", "Female", "Other")
        val genderAdapter = ArrayAdapter(this, R.layout.spinner_item, genders)
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        genderSpinner.adapter = genderAdapter

        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("Users").document(userId).get()
                .addOnSuccessListener { doc ->
                    if (doc != null && doc.exists()) {
                        val email = doc.getString("email")
                        val gid = doc.getString("gjengeID") ?: "GPL-NEW"
                        val nID = doc.getString("nationalID") ?: ""
                        val loc = doc.getString("location") ?: ""
                        val gender = doc.getString("gender") ?: "Male"

                        profileNameLarge.text = email
                        gjengeIdLarge.text = gid
                        nationalIdInput.setText(nID)
                        locationInput.setText(loc)
                        
                        val pos = genders.indexOf(gender)
                        if (pos >= 0) genderSpinner.setSelection(pos)
                        
                        updateProfilePicture(gender)
                    }
                }
        }

        saveProfileBtn.setOnClickListener {
            val nID = nationalIdInput.text.toString()
            val loc = locationInput.text.toString()
            val gen = genderSpinner.selectedItem.toString()

            if (userId != null) {
                val updates = hashMapOf(
                    "nationalID" to nID,
                    "location" to loc,
                    "gender" to gen
                )
                db.collection("Users").document(userId).update(updates as Map<String, Any>)
                    .addOnSuccessListener {
                        updateProfilePicture(gen)
                        Toast.makeText(this, "Profile Saved Successfully!", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        btnUploadFront.setOnClickListener {
            Toast.makeText(this, "Select Front Side of your National ID", Toast.LENGTH_SHORT).show()
        }

        btnUploadBack.setOnClickListener {
            Toast.makeText(this, "Select Back Side of your National ID", Toast.LENGTH_SHORT).show()
        }

        backBtn.setOnClickListener {
            finish()
        }
    }

    private fun updateProfilePicture(gender: String) {
        when (gender) {
            "Male" -> profileImageLarge.setImageResource(R.drawable.male_placeholder)
            "Female" -> profileImageLarge.setImageResource(R.drawable.female_placeholder)
            else -> profileImageLarge.setImageResource(R.drawable.g4e_no_bg)
        }
    }
}

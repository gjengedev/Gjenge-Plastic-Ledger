package com.example.gjengeplasticledger

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class ProfileActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    
    private lateinit var profileImageLarge: ImageView
    private lateinit var genderSpinner: Spinner
    private lateinit var idFrontPreview: ImageView
    private lateinit var idBackPreview: ImageView
    
    private var frontUri: Uri? = null
    private var backUri: Uri? = null

    private val pickFrontLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            frontUri = uri
            idFrontPreview.setImageURI(uri)
            idFrontPreview.imageTintList = null
            idFrontPreview.scaleType = ImageView.ScaleType.CENTER_CROP
        }
    }

    private val pickBackLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            backUri = uri
            idBackPreview.setImageURI(uri)
            idBackPreview.imageTintList = null
            idBackPreview.scaleType = ImageView.ScaleType.CENTER_CROP
        }
    }

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
        idFrontPreview = findViewById(R.id.idFrontImage)
        idBackPreview = findViewById(R.id.idBackImage)

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

        btnUploadFront.setOnClickListener { pickFrontLauncher.launch("image/*") }
        btnUploadBack.setOnClickListener { pickBackLauncher.launch("image/*") }

        saveProfileBtn.setOnClickListener {
            val nID = nationalIdInput.text.toString()
            val loc = locationInput.text.toString()
            val gen = genderSpinner.selectedItem.toString()

            if (userId != null) {
                uploadIdPhotosAndSave(userId, nID, loc, gen)
            }
        }


        backBtn.setOnClickListener {
            navigateToDashboard()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToDashboard()
            }
        })
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, CollectorDashboard::class.java)
        intent.putExtra("TARGET_TAB", "HOME")
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private fun uploadIdPhotosAndSave(userId: String, nID: String, loc: String, gen: String) {
        val updates = HashMap<String, Any>()
        updates["nationalID"] = nID
        updates["location"] = loc
        updates["gender"] = gen

        if (frontUri != null) {
            val ref = storage.reference.child("ids/$userId-front.jpg")
            ref.putFile(frontUri!!).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { url ->
                    updates["idFrontUrl"] = url.toString()
                    uploadBackPhoto(userId, updates)
                }
            }
        } else {
            uploadBackPhoto(userId, updates)
        }
    }

    private fun uploadBackPhoto(userId: String, updates: HashMap<String, Any>) {
        if (backUri != null) {
            val ref = storage.reference.child("ids/$userId-back.jpg")
            ref.putFile(backUri!!).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { url ->
                    updates["idBackUrl"] = url.toString()
                    saveToFirestore(userId, updates)
                }
            }
        } else {
            saveToFirestore(userId, updates)
        }
    }

    private fun saveToFirestore(userId: String, updates: HashMap<String, Any>) {
        db.collection("Users").document(userId).update(updates as Map<String, Any>)
            .addOnSuccessListener {
                updateProfilePicture(updates["gender"] as String)
                Toast.makeText(this, "Profile Saved Successfully!", Toast.LENGTH_SHORT).show()
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

package com.example.gjengeplasticledger

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.util.*

class ProfileActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private lateinit var profileImageLarge: ImageView
    private lateinit var firstNameInput: EditText
    private lateinit var middleNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var genderSpinner: Spinner
    private lateinit var idFrontPreview: ImageView
    private lateinit var idBackPreview: ImageView
    private lateinit var uploadSection: LinearLayout
    private lateinit var verifyStatusText: TextView

    private var frontUri: Uri? = null
    private var backUri: Uri? = null
    private var profileUri: Uri? = null
    private var currentCollectorName = "Collector"

    private val pickProfileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            profileUri = uri
            profileImageLarge.setImageURI(uri)
            profileImageLarge.scaleType = ImageView.ScaleType.CENTER_CROP
        }
    }

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
        verifyStatusText = findViewById(R.id.verifyStatusText)

        firstNameInput = findViewById(R.id.firstNameInput)
        middleNameInput = findViewById(R.id.middleNameInput)
        lastNameInput = findViewById(R.id.lastNameInput)
        val nationalIdInput = findViewById<EditText>(R.id.nationalIdInput)
        val locationInput = findViewById<EditText>(R.id.locationInput)
        genderSpinner = findViewById(R.id.genderSpinner)
        val saveProfileBtn = findViewById<Button>(R.id.saveProfileBtn)

        uploadSection = findViewById(R.id.uploadSection)
        val btnUploadFront = findViewById<LinearLayout>(R.id.btnUploadFront)
        val btnUploadBack = findViewById<LinearLayout>(R.id.btnUploadBack)
        val btnEditProfilePic = findViewById<View>(R.id.btnEditProfilePic)
        idFrontPreview = findViewById(R.id.idFrontImage)
        idBackPreview = findViewById(R.id.idBackImage)

        btnEditProfilePic.setOnClickListener { pickProfileLauncher.launch("image/*") }

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
                        val fName = doc.getString("firstName") ?: ""
                        val mName = doc.getString("middleName") ?: ""
                        val lName = doc.getString("lastName") ?: ""
                        val nID = doc.getString("nationalID") ?: ""
                        val loc = doc.getString("location") ?: ""
                        val gender = doc.getString("gender") ?: "Male"
                        val isVerified = doc.getBoolean("idVerified") ?: false
                        val profileBase64 = doc.getString("profileImageBase64") ?: ""

                        currentCollectorName = fName.ifEmpty { email?.split("@")?.get(0) ?: "Collector" }
                        profileNameLarge.text = email
                        gjengeIdLarge.text = gid
                        firstNameInput.setText(fName)
                        middleNameInput.setText(mName)
                        lastNameInput.setText(lName)
                        nationalIdInput.setText(nID)
                        locationInput.setText(loc)
                        
                        if (profileBase64.isNotEmpty()) {
                            val decodedString = Base64.decode(profileBase64, Base64.DEFAULT)
                            val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                            profileImageLarge.setImageBitmap(decodedByte)
                        } else {
                            updateProfilePicture(gender)
                        }

                        val pos = genders.indexOf(gender)
                        if (pos >= 0) genderSpinner.setSelection(pos)

                        updateProfilePicture(gender)

                        if (isVerified) {
                            verifyStatusText.text = getString(R.string.identity_verified)
                            verifyStatusText.setTextColor(android.graphics.Color.parseColor("#16A34A"))
                            uploadSection.visibility = View.GONE
                            saveProfileBtn.visibility = View.GONE
                            firstNameInput.isEnabled = false
                            middleNameInput.isEnabled = false
                            lastNameInput.isEnabled = false
                            nationalIdInput.isEnabled = false
                            locationInput.isEnabled = false
                            genderSpinner.isEnabled = false
                        } else {
                            verifyStatusText.text = getString(R.string.identity_not_verified)
                            verifyStatusText.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
                        }
                    }
                }
        }

        btnUploadFront.setOnClickListener { pickFrontLauncher.launch("image/*") }
        btnUploadBack.setOnClickListener { pickBackLauncher.launch("image/*") }

        saveProfileBtn.setOnClickListener {
            val fName = firstNameInput.text.toString()
            val mName = middleNameInput.text.toString()
            val lName = lastNameInput.text.toString()
            val nID = nationalIdInput.text.toString()
            val loc = locationInput.text.toString()
            val gen = genderSpinner.selectedItem.toString()

            if (fName.isEmpty() || lName.isEmpty() || nID.isEmpty()) {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Confirm Save")
                .setMessage(R.string.save_confirmation)
                .setPositiveButton(R.string.yes) { _, _ ->
                    if (userId != null) {
                        uploadIdPhotosAndSave(userId, fName, mName, lName, nID, loc, gen)
                    }
                }
                .setNegativeButton(R.string.no, null)
                .show()
        }

        backBtn.setOnClickListener { navigateToDashboard() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { navigateToDashboard() }
        })
    }

    private fun navigateToDashboard() {
        val intent = android.content.Intent(this, CollectorDashboard::class.java)
        intent.putExtra("TARGET_TAB", "HOME")
        intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private fun uploadIdPhotosAndSave(userId: String, fName: String, mName: String, lName: String, nID: String, loc: String, gen: String) {
        val updates = HashMap<String, Any>()
        updates["firstName"] = fName
        updates["middleName"] = mName
        updates["lastName"] = lName
        updates["nationalID"] = nID
        updates["location"] = loc
        updates["gender"] = gen

        if (profileUri != null) {
            val profileBase64 = uriToBase64(profileUri!!)
            if (profileBase64 != null) updates["profileImageBase64"] = profileBase64
        }

        // Convert images to Base64 strings instead of uploading to Firebase Storage
        if (frontUri != null) {
            val frontBase64 = uriToBase64(frontUri!!)
            if (frontBase64 != null) updates["idFrontBase64"] = frontBase64
        }
        
        if (backUri != null) {
            val backBase64 = uriToBase64(backUri!!)
            if (backBase64 != null) updates["idBackBase64"] = backBase64
        }

        saveToFirestore(userId, updates)
    }

    private fun uriToBase64(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Resize and compress to keep under 1MB Firestore limit
            val scaledBitmap = scaleBitmap(bitmap)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun scaleBitmap(source: Bitmap): Bitmap {
        val maxSize = 800
        var width = source.width
        var height = source.height
        val bitmapRatio = width.toFloat() / height.toFloat()
        if (bitmapRatio > 1) {
            width = maxSize
            height = (width / bitmapRatio).toInt()
        } else {
            height = maxSize
            width = (height * bitmapRatio).toInt()
        }
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun saveToFirestore(userId: String, updates: HashMap<String, Any>) {
        db.collection("Users").document(userId).update(updates as Map<String, Any>)
            .addOnSuccessListener {
                updateProfilePicture(updates["gender"] as String)
                Toast.makeText(this, getString(R.string.profile_saved), Toast.LENGTH_SHORT).show()
                navigateToDashboard()
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save profile: ${e.message}", Toast.LENGTH_LONG).show()
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

package com.example.gjengeplasticledger

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import java.util.Locale
import java.util.UUID

class CollectorDashboard : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private lateinit var profileImage: ImageView
    private lateinit var plasticTypeSpinner: Spinner
    private lateinit var logsContainer: LinearLayout
    private lateinit var totalEarningsText: TextView
    private lateinit var logPlasticCard: MaterialCardView
    private lateinit var plasticPhotoPreview: ImageView
    private var selectedImageUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            plasticPhotoPreview.setImageURI(uri)
            plasticPhotoPreview.imageTintList = null // Remove the gray tint from the icon
            plasticPhotoPreview.scaleType = ImageView.ScaleType.CENTER_CROP
        }
    }

    data class PlasticType(val name: String, val description: String, val imageRes: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collector_dashboard)

        // Initialize Views
        profileImage = findViewById(R.id.profileImage)
        val profileName = findViewById<TextView>(R.id.profileName)
        val gjengeIdDisplay = findViewById<TextView>(R.id.gjengeIdDisplay)
        totalEarningsText = findViewById(R.id.totalEarningsText)

        logPlasticCard = findViewById(R.id.logPlasticCard)
        val showLogSectionBtn = findViewById<Button>(R.id.showLogSectionBtn)
        val plasticInput = findViewById<EditText>(R.id.plasticInput)
        plasticTypeSpinner = findViewById(R.id.plasticTypeSpinner)
        val btnUploadPlasticPhoto = findViewById<LinearLayout>(R.id.btnUploadPlasticPhoto)
        plasticPhotoPreview = findViewById(R.id.plasticPhotoPreview)
        val addBtn = findViewById<Button>(R.id.addBtn)
        
        logsContainer = findViewById(R.id.logsContainer)

        btnUploadPlasticPhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        val fabCenter = findViewById<View>(R.id.fab_center)
        fabCenter.setOnClickListener {
            logPlasticCard.isVisible = !logPlasticCard.isVisible
        }

        val withdrawBtn = findViewById<Button>(R.id.withdrawBtn)
        withdrawBtn.setOnClickListener {
            Toast.makeText(this, "Withdrawal request sent to Admin", Toast.LENGTH_SHORT).show()
        }

        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_home -> Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show()
                R.id.nav_wallet -> Toast.makeText(this, "Wallet", Toast.LENGTH_SHORT).show()
                R.id.nav_analytics -> Toast.makeText(this, "Statistics", Toast.LENGTH_SHORT).show()
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                }
            }
            true
        }

        val logoutBtn = findViewById<Button>(R.id.logoutBtn)
        logoutBtn.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Toggle Log Section
        showLogSectionBtn.setOnClickListener {
            logPlasticCard.isVisible = !logPlasticCard.isVisible
        }

        // Setup Plastic Type Spinner with Images
        setupPlasticSpinner()

        val userId = auth.currentUser?.uid
        if (userId != null) {
            fetchUserDetails(userId, profileName, gjengeIdDisplay)
            listenForLogs(userId)
        }

        // Log Plastic Logic
        addBtn.setOnClickListener {
            val kg = plasticInput.text.toString().toDoubleOrNull()
            val selectedItem = plasticTypeSpinner.selectedItem as? PlasticType
            val type = selectedItem?.name ?: "PET"
            
            if (kg != null && userId != null) {
                val rates = mapOf("PET" to 15.0, "HDPE" to 20.0, "LDPE" to 18.0)
                val creditsMap = mapOf("PET" to 10, "HDPE" to 15, "LDPE" to 12)
                
                val payment = kg * (rates[type] ?: 0.0)
                val credits = (kg * (creditsMap[type] ?: 0)).toInt()

                val log = hashMapOf(
                    "weight" to kg,
                    "type" to type,
                    "payment" to payment,
                    "credits" to credits,
                    "status" to "Pending",
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("Users").document(userId).collection("Logs").add(log)
                    .addOnSuccessListener {
                        plasticInput.text.clear()
                        logPlasticCard.visibility = View.GONE
                        Toast.makeText(this, "Log submitted for approval", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Enter valid weight", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupPlasticSpinner() {
        val plasticList = listOf(
            PlasticType("PET", "Water & soda bottles", R.drawable.ic_plastic_pet),
            PlasticType("HDPE", "Milk jugs & shampoo bottles", R.drawable.ic_plastic_hdpe),
            PlasticType("LDPE", "Plastic bags & squeeze bottles", R.drawable.ic_plastic_ldpe)
        )

        val adapter = object : ArrayAdapter<PlasticType>(this, R.layout.spinner_item_with_image, plasticList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return createViewFromResource(position, convertView, parent)
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return createViewFromResource(position, convertView, parent)
            }

            private fun createViewFromResource(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.spinner_item_with_image, parent, false)
                val item = getItem(position)
                
                val img = view.findViewById<ImageView>(R.id.spinnerImage)
                val title = view.findViewById<TextView>(R.id.spinnerTitle)
                val sub = view.findViewById<TextView>(R.id.spinnerSub)

                item?.let {
                    img.setImageResource(it.imageRes)
                    title.text = it.name
                    sub.text = it.description
                }
                return view
            }
        }
        plasticTypeSpinner.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        // Refresh profile details in case they were updated in ProfileActivity
        val userId = auth.currentUser?.uid
        if (userId != null) {
            val profileName = findViewById<TextView>(R.id.profileName)
            val gjengeIdDisplay = findViewById<TextView>(R.id.gjengeIdDisplay)
            fetchUserDetails(userId, profileName, gjengeIdDisplay)
        }
    }

    private fun fetchUserDetails(userId: String, nameTxt: TextView, idTxt: TextView) {
        db.collection("Users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val email = doc.getString("email")
                    val gid = doc.getString("gjengeID") ?: "GPL-NEW"
                    val gender = doc.getString("gender") ?: "Male"

                    val firstName = email?.split("@")?.get(0) ?: "Collector"
                    nameTxt.text = getString(R.string.hello_collector_name, firstName)
                    idTxt.text = gid
                    
                    updateProfilePicture(gender)
                }
            }
    }

    private fun listenForLogs(userId: String) {
        db.collection("Users").document(userId).collection("Logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener

                logsContainer.removeAllViews()
                var totalEstimated = 0.0

                snapshots?.forEach { doc ->
                    val kg = doc.getDouble("weight") ?: 0.0
                    val type = doc.getString("type") ?: ""
                    val payment = doc.getDouble("payment") ?: 0.0
                    val status = doc.getString("status") ?: "Pending"
                    
                    if (status != "Paid") {
                        totalEstimated += payment
                    }

                    addLogView(kg, type, payment, status)
                }
                totalEarningsText.text = String.format(Locale.getDefault(), "%s%.2f", getString(R.string.currency_ksh), totalEstimated)
            }
    }

    private fun addLogView(kg: Double, type: String, payment: Double, status: String) {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12.dpToPx()) }
            radius = 12.dpToPx().toFloat()
            setCardBackgroundColor(Color.WHITE)
            cardElevation = 2.dpToPx().toFloat()
        }

        val layout = RelativeLayout(this).apply {
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
        }

        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }

        val title = TextView(this).apply {
            text = getString(R.string.collection_item_title, type)
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val subTitle = TextView(this).apply {
            text = getString(R.string.collection_item_subtitle, kg, payment)
            textSize = 12f
            setTextColor(Color.GRAY)
        }

        infoLayout.addView(title)
        infoLayout.addView(subTitle)

        val statusTxt = TextView(this).apply {
            text = status
            textSize = 12f
            setTextColor(if (status == "Approved") Color.parseColor("#16A34A") else Color.parseColor("#CA8A04"))
            val lp = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply { addRule(RelativeLayout.ALIGN_PARENT_END); addRule(RelativeLayout.CENTER_VERTICAL) }
            layoutParams = lp
            setPadding(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 4.dpToPx())
            setBackgroundResource(android.R.drawable.editbox_dropdown_light_frame)
        }

        layout.addView(infoLayout)
        layout.addView(statusTxt)
        card.addView(layout)
        logsContainer.addView(card)
    }

    private fun updateProfilePicture(gender: String) {
        when (gender) {
            "Male" -> profileImage.setImageResource(R.drawable.male_placeholder)
            "Female" -> profileImage.setImageResource(R.drawable.female_placeholder)
            else -> profileImage.setImageResource(R.drawable.g4e_no_bg)
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}

package com.example.gjengeplasticledger

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.*

class CollectorDashboard : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    
    private lateinit var profileImage: ImageView
    private lateinit var plasticTypeSpinner: Spinner
    private lateinit var logsContainer: LinearLayout
    private lateinit var allStatsContainer: LinearLayout
    private lateinit var totalEarningsText: TextView
    private lateinit var totalCreditsText: TextView
    private lateinit var logPlasticCard: MaterialCardView
    private lateinit var plasticPhotoPreview: ImageView
    private lateinit var statsPieChart: PieChart
    
    private lateinit var homeContainer: View
    private lateinit var walletContainer: View
    private lateinit var statsContainer: View
    
    private var selectedImageUri: Uri? = null
    private var currentTotalEarnings = 0.0
    private var currentTotalCredits = 0
    private var currentNationalId = ""
    private var isIdUploaded = false

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            plasticPhotoPreview.setImageURI(uri)
            plasticPhotoPreview.imageTintList = null
            plasticPhotoPreview.scaleType = ImageView.ScaleType.CENTER_CROP
        }
    }

    data class PlasticType(val name: String, val description: String, val imageRes: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collector_dashboard)

        // Initialize Containers
        homeContainer = findViewById(R.id.home_container)
        walletContainer = findViewById(R.id.wallet_container)
        statsContainer = findViewById(R.id.stats_container)

        // Initialize Views
        profileImage = findViewById(R.id.profileImage)
        val profileName = findViewById<TextView>(R.id.profileName)
        val gjengeIdDisplay = findViewById<TextView>(R.id.gjengeIdDisplay)
        totalEarningsText = findViewById(R.id.walletTotalEarningsText)
        totalCreditsText = findViewById(R.id.totalCreditsText)

        logPlasticCard = findViewById(R.id.logPlasticCard)
        val plasticInput = findViewById<EditText>(R.id.plasticInput)
        plasticTypeSpinner = findViewById(R.id.plasticTypeSpinner)
        val btnUploadPlasticPhoto = findViewById<LinearLayout>(R.id.btnUploadPlasticPhoto)
        plasticPhotoPreview = findViewById(R.id.plasticPhotoPreview)
        val addBtn = findViewById<Button>(R.id.addBtn)
        
        logsContainer = findViewById(R.id.logsContainer)
        allStatsContainer = findViewById(R.id.allStatsContainer)
        statsPieChart = findViewById(R.id.statsPieChart)

        // Bottom Navigation
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            hideAllContainers()
            when(item.itemId) {
                R.id.nav_home -> homeContainer.isVisible = true
                R.id.nav_wallet -> walletContainer.isVisible = true
                R.id.nav_analytics -> statsContainer.isVisible = true
                R.id.nav_profile -> startActivity(Intent(this, ProfileActivity::class.java))
            }
            true
        }

        findViewById<View>(R.id.fab_center).setOnClickListener {
            hideAllContainers()
            homeContainer.isVisible = true
            logPlasticCard.isVisible = !logPlasticCard.isVisible
        }

        findViewById<Button>(R.id.showLogSectionBtn).setOnClickListener {
            logPlasticCard.isVisible = !logPlasticCard.isVisible
        }

        // Handle withdrawal from both Dashboard and Wallet page
        val withdrawBtnHome = findViewById<Button>(R.id.withdrawBtnHome)
        val withdrawBtnWallet = findViewById<Button>(R.id.withdrawBtnWallet)
        
        val withdrawListener = View.OnClickListener {
            showWithdrawDialog()
        }
        withdrawBtnHome.setOnClickListener(withdrawListener)
        withdrawBtnWallet.setOnClickListener(withdrawListener)

        findViewById<Button>(R.id.logoutBtn).setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        btnUploadPlasticPhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        setupPlasticSpinner()

        val userId = auth.currentUser?.uid
        if (userId != null) {
            fetchUserDetails(userId, profileName, gjengeIdDisplay)
            listenForLogs(userId)
        }

        addBtn.setOnClickListener {
            val kg = plasticInput.text.toString().toDoubleOrNull()
            val selectedItem = plasticTypeSpinner.selectedItem as? PlasticType
            val type = selectedItem?.name ?: "PET"
            
            if (kg != null && userId != null) {
                uploadLog(userId, kg, type, plasticInput)
            } else {
                Toast.makeText(this, "Enter valid weight", Toast.LENGTH_SHORT).show()
            }
        }

        handleTargetTab(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTargetTab(intent)
    }

    private fun handleTargetTab(intent: Intent?) {
        val target = intent?.getStringExtra("TARGET_TAB")
        if (target == "HOME") {
            hideAllContainers()
            homeContainer.isVisible = true
            findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation).selectedItemId = R.id.nav_home
        }
    }

    private fun hideAllContainers() {
        homeContainer.isVisible = false
        walletContainer.isVisible = false
        statsContainer.isVisible = false
    }

    private fun showWithdrawDialog() {
        if (!isIdUploaded) {
            Toast.makeText(this, getString(R.string.upload_required), Toast.LENGTH_LONG).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_withdraw, null)
        val amountIn = dialogView.findViewById<EditText>(R.id.withdrawAmount)
        val idIn = dialogView.findViewById<EditText>(R.id.withdrawIdConfirm)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.withdraw_prompt))
            .setView(dialogView)
            .setPositiveButton("Withdraw") { _, _ ->
                val amount = amountIn.text.toString().toDoubleOrNull() ?: 0.0
                val confirmedId = idIn.text.toString()

                if (amount > 0 && amount <= currentTotalEarnings && confirmedId == currentNationalId) {
                    processWithdrawal(amount)
                } else {
                    Toast.makeText(this, getString(R.string.insufficient_funds), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun processWithdrawal(amount: Double) {
        val userId = auth.currentUser?.uid ?: return
        val newEarnings = currentTotalEarnings - amount
        val newCredits = currentTotalCredits - amount.toInt()
        
        val updates = mapOf(
            "totalEarnings" to newEarnings,
            "totalCredits" to if (newCredits > 0) newCredits else 0
        )
        
        db.collection("Users").document(userId).update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, getString(R.string.withdrawal_success), Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadLog(userId: String, kg: Double, type: String, input: EditText) {
        val rates = mapOf("PET" to 15.0, "HDPE" to 20.0, "LDPE" to 18.0)
        val creditsMap = mapOf("PET" to 10, "HDPE" to 15, "LDPE" to 12)
        val payment = kg * (rates[type] ?: 0.0)
        val credits = (kg * (creditsMap[type] ?: 0)).toInt()

        val logId = UUID.randomUUID().toString()
        if (selectedImageUri != null) {
            val ref = storage.reference.child("logs/$logId.jpg")
            ref.putFile(selectedImageUri!!).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { url ->
                    saveLogToFirestore(userId, kg, type, payment, credits, url.toString(), input)
                }
            }
        } else {
            saveLogToFirestore(userId, kg, type, payment, credits, "", input)
        }
    }

    private fun saveLogToFirestore(userId: String, kg: Double, type: String, payment: Double, credits: Int, imageUrl: String, input: EditText) {
        val log = hashMapOf(
            "weight" to kg,
            "type" to type,
            "payment" to payment,
            "credits" to credits,
            "status" to "Pending",
            "imageUrl" to imageUrl,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("Users").document(userId).collection("Logs").add(log)
            .addOnSuccessListener {
                input.text.clear()
                selectedImageUri = null
                plasticPhotoPreview.setImageResource(android.R.drawable.ic_menu_camera)
                logPlasticCard.visibility = View.GONE
                Toast.makeText(this, "Log submitted for approval", Toast.LENGTH_SHORT).show()
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

    private fun fetchUserDetails(userId: String, nameTxt: TextView, idTxt: TextView) {
        db.collection("Users").document(userId).addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                val email = doc.getString("email")
                val gid = doc.getString("gjengeID") ?: "GPL-NEW"
                val gender = doc.getString("gender") ?: "Male"
                currentNationalId = doc.getString("nationalID") ?: ""
                
                val front = doc.getString("idFrontUrl") ?: ""
                val back = doc.getString("idBackUrl") ?: ""
                isIdUploaded = front.isNotEmpty() && back.isNotEmpty()

                val firstName = email?.split("@")?.get(0) ?: "Collector"
                nameTxt.text = getString(R.string.hello_collector_name, firstName)
                idTxt.text = gid
                
                currentTotalEarnings = doc.getDouble("totalEarnings") ?: 0.0
                currentTotalCredits = doc.getLong("totalCredits")?.toInt() ?: 0
                
                totalEarningsText.text = String.format(Locale.getDefault(), "KSh %.2f", currentTotalEarnings)
                totalCreditsText.text = getString(R.string.total_credits_display, currentTotalCredits)
                
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
                allStatsContainer.removeAllViews()
                
                val monthCounts = mutableMapOf<String, Int>()
                val sdf = SimpleDateFormat("MMM yyyy", Locale.getDefault())

                snapshots?.forEach { doc ->
                    val kg = doc.getDouble("weight") ?: 0.0
                    val type = doc.getString("type") ?: ""
                    val payment = doc.getDouble("payment") ?: 0.0
                    val status = doc.getString("status") ?: "Pending"
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    
                    val month = sdf.format(Date(timestamp))
                    monthCounts[month] = (monthCounts[month] ?: 0) + 1

                    addLogView(kg, type, payment, status, logsContainer)
                    addLogView(kg, type, payment, status, allStatsContainer)
                }
                updatePieChart(monthCounts)
            }
    }

    private fun updatePieChart(data: Map<String, Int>) {
        val entries = data.map { PieEntry(it.value.toFloat(), it.key) }
        val dataSet = PieDataSet(entries, "Logs per Month")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.valueTextColor = Color.BLACK
        dataSet.valueTextSize = 12f

        statsPieChart.data = PieData(dataSet)
        statsPieChart.description.isEnabled = false
        statsPieChart.centerText = "Activity"
        statsPieChart.animateY(1000)
        statsPieChart.invalidate()
    }

    private fun addLogView(kg: Double, type: String, payment: Double, status: String, container: LinearLayout) {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12.dpToPx()) }
            radius = 12.dpToPx().toFloat()
            setCardBackgroundColor(Color.WHITE)
            cardElevation = 2.dpToPx().toFloat()
        }
        val layout = RelativeLayout(this).apply { setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx()) }
        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        infoLayout.addView(TextView(this).apply {
            text = getString(R.string.collection_item_title, type)
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        infoLayout.addView(TextView(this).apply {
            text = getString(R.string.collection_item_subtitle, kg, payment)
            textSize = 12f
            setTextColor(Color.GRAY)
        })
        val statusTxt = TextView(this).apply {
            text = status
            textSize = 12f
            setTextColor(if (status == "Approved") ContextCompat.getColor(context, android.R.color.holo_green_dark) else ContextCompat.getColor(context, android.R.color.holo_orange_dark))
            layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            setPadding(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 4.dpToPx())
            setBackgroundResource(android.R.drawable.editbox_dropdown_light_frame)
        }
        layout.addView(infoLayout)
        layout.addView(statusTxt)
        card.addView(layout)
        container.addView(card)
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

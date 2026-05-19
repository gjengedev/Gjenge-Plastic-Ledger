package com.example.gjengeplasticledger

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
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
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class CollectorDashboard : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private lateinit var profileImage: ImageView
    private lateinit var plasticTypeSpinner: Spinner
    private lateinit var logsContainer: LinearLayout
    private lateinit var allStatsContainer: LinearLayout
    private lateinit var totalEarningsText: TextView
    private lateinit var totalCreditsText: TextView
    private lateinit var withdrawalsContainer: LinearLayout
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

    private var currentCollectorName = "Collector"

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
        withdrawalsContainer = findViewById(R.id.withdrawalsContainer)

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

        // Handle withdrawal and redemption
        val redeemBtnHome = findViewById<Button>(R.id.redeemCreditsBtnHome)
        val withdrawBtnWallet = findViewById<Button>(R.id.withdrawBtnWallet)
        
        redeemBtnHome.setOnClickListener {
            showRedeemCreditsDialog()
        }
        withdrawBtnWallet.setOnClickListener {
            showWithdrawDialog()
        }

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
            listenForWithdrawals(userId)
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

    private fun showRedeemCreditsDialog() {
        val input = EditText(this).apply {
            hint = "Credits to redeem"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(20, 20, 20, 20)
        }

        AlertDialog.Builder(this)
            .setTitle("Redeem Credits")
            .setMessage("Current Credits: $currentTotalCredits pts\nEnter amount to redeem:")
            .setView(input)
            .setPositiveButton("Redeem") { _, _ ->
                val creditsToRedeem = input.text.toString().toIntOrNull() ?: 0
                if (creditsToRedeem > 0 && creditsToRedeem <= currentTotalCredits) {
                    processRedemption(creditsToRedeem)
                } else {
                    Toast.makeText(this, "Insufficient credits", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun processRedemption(creditsToRedeem: Int) {
        val userId = auth.currentUser?.uid ?: return
        val newCredits = currentTotalCredits - creditsToRedeem

        val batch = db.batch()
        val userRef = db.collection("Users").document(userId)
        batch.update(userRef, "totalCredits", newCredits)

        val redemptionRef = userRef.collection("Redemptions").document()
        val redemptionData = hashMapOf(
            "amount" to creditsToRedeem,
            "timestamp" to System.currentTimeMillis(),
            "type" to "Redemption"
        )
        batch.set(redemptionRef, redemptionData)

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "$creditsToRedeem credits redeemed successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to redeem credits: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun processWithdrawal(amount: Double) {
        val userId = auth.currentUser?.uid ?: return
        
        val withdrawalRequest = hashMapOf(
            "userId" to userId,
            "collectorName" to currentCollectorName,
            "amount" to amount,
            "nationalID" to currentNationalId,
            "status" to "Pending",
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("Withdrawals").add(withdrawalRequest)
            .addOnSuccessListener {
                Toast.makeText(this, "Withdrawal request submitted for Admin approval", Toast.LENGTH_LONG).show()
            }
    }

    private fun uploadLog(userId: String, kg: Double, type: String, input: EditText) {
        val rates = mapOf("PET" to 15.0, "HDPE" to 20.0, "LDPE" to 18.0)
        val creditsMap = mapOf("PET" to 10, "HDPE" to 15, "LDPE" to 12)
        val payment = kg * (rates[type] ?: 0.0)
        val credits = (kg * (creditsMap[type] ?: 0)).toInt()

        var imageBase64 = ""
        if (selectedImageUri != null) {
            imageBase64 = uriToBase64(selectedImageUri!!) ?: ""
        }
        
        saveLogToFirestore(userId, kg, type, payment, credits, imageBase64, input)
    }

    private fun uriToBase64(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

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
        val maxSize = 600 // Smaller for logs to save space
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

    private fun saveLogToFirestore(userId: String, kg: Double, type: String, payment: Double, credits: Int, imageBase64: String, input: EditText) {
        val log = hashMapOf(
            "weight" to kg,
            "type" to type,
            "payment" to payment,
            "credits" to credits,
            "status" to "Pending",
            "imageBase64" to imageBase64,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("Users").document(userId).collection("Logs").add(log)
            .addOnSuccessListener {
                input.text.clear()
                selectedImageUri = null
                plasticPhotoPreview.setImageResource(android.R.drawable.ic_menu_camera)
                logPlasticCard.visibility = View.GONE
                Toast.makeText(this, "Log submitted for approval", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save log: ${e.message}", Toast.LENGTH_LONG).show()
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
                val firstName = doc.getString("firstName") ?: ""
                val lastName = doc.getString("lastName") ?: ""
                val gender = doc.getString("gender") ?: "Male"
                currentNationalId = doc.getString("nationalID") ?: ""

                val front = doc.getString("idFrontBase64") ?: ""
                val back = doc.getString("idBackBase64") ?: ""
                isIdUploaded = front.isNotEmpty() && back.isNotEmpty()

                val displayName = if (firstName.isNotEmpty()) "$firstName $lastName" else email?.split("@")?.get(0) ?: "Collector"
                currentCollectorName = if (firstName.isNotEmpty()) firstName else displayName
                nameTxt.text = getString(R.string.hello_collector_name, displayName)
                idTxt.text = gid
                
                val profileBase64 = doc.getString("profileImageBase64") ?: ""
                if (profileBase64.isNotEmpty()) {
                    val decodedString = Base64.decode(profileBase64, Base64.DEFAULT)
                    val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                    profileImage.setImageBitmap(decodedByte)
                } else {
                    updateProfilePicture(gender)
                }
                
                currentTotalEarnings = doc.getDouble("totalEarnings") ?: 0.0
                currentTotalCredits = doc.getLong("totalCredits")?.toInt() ?: 0
                
                totalEarningsText.text = String.format(Locale.getDefault(), "KSh %.2f", currentTotalEarnings)
                totalCreditsText.text = getString(R.string.total_credits_display, currentTotalCredits)
                
                updateProfilePicture(gender)
            }
        }
    }

    private fun listenForLogs(userId: String) {
        val userDoc = db.collection("Users").document(userId)
        
        // Listen to Logs
        userDoc.collection("Logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                updateStatsList(userId)
            }
            
        // Listen to Redemptions
        userDoc.collection("Redemptions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                updateStatsList(userId)
            }
    }

    private fun updateStatsList(userId: String) {
        val userDoc = db.collection("Users").document(userId)
        
        // Fetch both and combine
        userDoc.collection("Logs").get().addOnSuccessListener { logs ->
            userDoc.collection("Redemptions").get().addOnSuccessListener { redemptions ->
                val combinedList = mutableListOf<Map<String, Any>>()
                
                logs.forEach { doc -> 
                    val data = doc.data.toMutableMap()
                    data["docType"] = "Log"
                    combinedList.add(data)
                }
                
                redemptions.forEach { doc -> 
                    val data = doc.data.toMutableMap()
                    data["docType"] = "Redemption"
                    combinedList.add(data)
                }
                
                // Sort by timestamp descending
                combinedList.sortByDescending { it["timestamp"] as Long }
                
                logsContainer.removeAllViews()
                allStatsContainer.removeAllViews()
                
                val monthCounts = mutableMapOf<String, Int>()
                val sdf = SimpleDateFormat("MMM yyyy", Locale.getDefault())

                combinedList.forEach { item ->
                    val type = item["docType"] as String
                    val timestamp = item["timestamp"] as Long
                    
                    if (type == "Log") {
                        val kg = (item["weight"] as? Number)?.toDouble() ?: 0.0
                        val plasticType = item["type"] as? String ?: ""
                        val payment = (item["payment"] as? Number)?.toDouble() ?: 0.0
                        val status = item["status"] as? String ?: "Pending"
                        
                        val month = sdf.format(Date(timestamp))
                        monthCounts[month] = (monthCounts[month] ?: 0) + 1

                        addLogView(kg, plasticType, payment, status, logsContainer)
                        addLogView(kg, plasticType, payment, status, allStatsContainer)
                    } else {
                        val amount = (item["amount"] as? Number)?.toInt() ?: 0
                        addRedemptionView(amount, timestamp, allStatsContainer)
                    }
                }
                updatePieChart(monthCounts)
            }
        }
    }

    private fun addRedemptionView(amount: Int, timestamp: Long, container: LinearLayout) {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(timestamp))

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12.dpToPx()) }
            radius = 12.dpToPx().toFloat()
            setCardBackgroundColor(Color.parseColor("#FFF4F4")) // Light red background for redemption
            cardElevation = 2.dpToPx().toFloat()
        }

        val layout = RelativeLayout(this).apply { setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx()) }
        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        
        infoLayout.addView(TextView(this).apply {
            text = "Redeemed: $amount Credits"
            textSize = 16f
            setTextColor(Color.parseColor("#991B1B"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        
        infoLayout.addView(TextView(this).apply {
            text = dateStr
            textSize = 12f
            setTextColor(Color.GRAY)
        })
        
        val statusTxt = TextView(this).apply {
            text = "Completed"
            textSize = 12f
            setTextColor(Color.parseColor("#991B1B"))
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

    private fun listenForWithdrawals(userId: String) {
        db.collection("Withdrawals")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    return@addSnapshotListener
                }

                withdrawalsContainer.removeAllViews()
                snapshots?.forEach { doc ->
                    val amount = doc.getDouble("amount") ?: 0.0
                    val status = doc.getString("status") ?: "Pending"
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    
                    addWithdrawalView(amount, status, timestamp)
                }
            }
    }

    private fun addWithdrawalView(amount: Double, status: String, timestamp: Long) {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(timestamp))

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
            text = "Withdrawal: KSh ${String.format("%.2f", amount)}"
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        infoLayout.addView(TextView(this).apply {
            text = dateStr
            textSize = 12f
            setTextColor(Color.GRAY)
        })

        val statusTxt = TextView(this).apply {
            text = status
            textSize = 12f
            val color = when (status) {
                "Paid" -> ContextCompat.getColor(context, android.R.color.holo_green_dark)
                "Approved" -> ContextCompat.getColor(context, android.R.color.holo_blue_dark)
                "Rejected" -> ContextCompat.getColor(context, android.R.color.holo_red_dark)
                else -> ContextCompat.getColor(context, android.R.color.holo_orange_dark)
            }
            setTextColor(color)
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
        withdrawalsContainer.addView(card)
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

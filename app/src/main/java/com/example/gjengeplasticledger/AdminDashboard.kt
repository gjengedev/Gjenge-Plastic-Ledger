package com.example.gjengeplasticledger

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class AdminDashboard : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var itemsList: LinearLayout
    private lateinit var collectorsList: LinearLayout
    private lateinit var collectorsGrid: GridLayout
    private lateinit var sectionTitle: TextView
    private val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    private val cardColors = listOf("#FEE2E2", "#E0F2FE", "#DCFCE7", "#FEF9C3", "#F3E8FF", "#FFEDD5")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        itemsList = findViewById(R.id.adminItemsList)
        collectorsList = findViewById(R.id.adminCollectorsList)
        collectorsGrid = findViewById(R.id.adminCollectorsGrid)
        sectionTitle = findViewById(R.id.adminSectionTitle)
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.admin_bottom_navigation)

        findViewById<Button>(R.id.adminLogoutBtn).setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.admin_nav_logs -> {
                    showSection("Logs")
                    listenForAllLogs()
                }
                R.id.admin_nav_withdrawals -> {
                    showSection("Withdrawals")
                    listenForWithdrawals()
                }
                R.id.admin_nav_collectors -> {
                    showSection("Collectors")
                    listenForCollectors()
                }
            }
            true
        }

        listenForAllLogs()
    }

    private fun showSection(type: String) {
        sectionTitle.text = when(type) {
            "Logs" -> "Plastic Collection Logs"
            "Withdrawals" -> "Pending Withdrawals"
            else -> "Registered Collectors"
        }
        itemsList.visibility = if (type == "Collectors") View.GONE else View.VISIBLE
        collectorsList.visibility = if (type == "Collectors") View.VISIBLE else View.GONE
        collectorsGrid.visibility = View.GONE
    }

    private fun listenForAllLogs() {
        db.collectionGroup("Logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                itemsList.removeAllViews()
                
                snapshots?.forEach { doc ->
                    val weight = doc.getDouble("weight") ?: 0.0
                    val type = doc.getString("type") ?: ""
                    val status = doc.getString("status") ?: "Pending"
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    val logId = doc.id
                    val userRef = doc.reference.parent.parent

                    userRef?.get()?.addOnSuccessListener { userDoc ->
                        val collectorName = userDoc.getString("firstName")?.let { "$it ${userDoc.getString("lastName") ?: ""}" } 
                            ?: userDoc.getString("email")?.split("@")?.get(0) ?: "Collector"
                        val gjengeId = userDoc.getString("gjengeID") ?: "GPL-NEW"
                        
                        val card = createAdminCard(
                            titleStr = "$type Collection - $weight Kg",
                            status = status,
                            time = timestamp,
                            collectorName = collectorName,
                            gjengeId = gjengeId
                        )
                        
                        if (status == "Pending") {
                            val btnLayout = LinearLayout(this).apply { 
                                orientation = LinearLayout.HORIZONTAL
                                setPadding(0, 16.dpToPx(), 0, 0)
                                gravity = Gravity.END
                            }
                            val approveBtn = Button(this).apply {
                                text = "Approve"
                                setBackgroundColor(Color.parseColor("#16A34A"))
                                setTextColor(Color.WHITE)
                                setOnClickListener { updateLogStatus(userRef.id, logId, "Approved") }
                            }
                            val denyBtn = Button(this).apply {
                                text = "Deny"
                                setBackgroundColor(Color.parseColor("#D32F2F"))
                                setTextColor(Color.WHITE)
                                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                                params.marginStart = 8.dpToPx()
                                layoutParams = params
                                setOnClickListener { updateLogStatus(userRef.id, logId, "Denied") }
                            }
                            btnLayout.addView(approveBtn)
                            btnLayout.addView(denyBtn)
                            (card.getChildAt(0) as LinearLayout).addView(btnLayout)
                        }
                        itemsList.addView(card)
                    }
                }
            }
    }

    private fun listenForWithdrawals() {
        db.collection("Withdrawals")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                itemsList.removeAllViews()

                snapshots?.forEach { doc ->
                    val name = doc.getString("collectorName") ?: "Collector"
                    val amount = doc.getDouble("amount") ?: 0.0
                    val status = doc.getString("status") ?: "Pending"
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    val userId = doc.getString("userId") ?: ""
                    val docId = doc.id

                    // Fetch the user's Gjenge ID as well
                    db.collection("Users").document(userId).get().addOnSuccessListener { userDoc ->
                        val gjengeId = userDoc.getString("gjengeID") ?: "GPL-NEW"
                        val card = createAdminCard(
                            titleStr = "Withdrawal: KSh $amount",
                            status = status,
                            time = timestamp,
                            collectorName = name,
                            gjengeId = gjengeId
                        )

                        if (status == "Pending") {
                            val btnLayout = LinearLayout(this).apply { 
                                orientation = LinearLayout.HORIZONTAL
                                setPadding(0, 16.dpToPx(), 0, 0)
                                gravity = Gravity.END
                            }
                            val approveBtn = Button(this).apply {
                                text = "Process & Email"
                                setBackgroundColor(Color.parseColor("#16A34A"))
                                setTextColor(Color.WHITE)
                                setOnClickListener { approveWithdrawal(docId, userId, amount) }
                            }
                            btnLayout.addView(approveBtn)
                            (card.getChildAt(0) as LinearLayout).addView(btnLayout)
                        }
                        itemsList.addView(card)
                    }
                }
            }
    }

    private fun listenForCollectors() {
        db.collection("Users").whereEqualTo("role", "Collector")
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                collectorsList.removeAllViews()

                snapshots?.forEachIndexed { index, doc ->
                    val email = doc.getString("email") ?: "User"
                    val firstName = doc.getString("firstName") ?: ""
                    val lastName = doc.getString("lastName") ?: ""
                    val displayName = if (firstName.isNotEmpty()) "$firstName $lastName" else email.split("@")[0]
                    
                    val gjengeId = doc.getString("gjengeID") ?: "GPL-NEW"
                    val gender = doc.getString("gender") ?: "Male"
                    val location = doc.getString("location") ?: "Unknown"
                    val color = cardColors[index % cardColors.size]
                    val userId = doc.id

                    db.collection("Users").document(userId).collection("Logs").get()
                        .addOnSuccessListener { logs ->
                            val logCount = logs.size()
                            val card = createCollectorCardNew(displayName, gjengeId, gender, location, color, logCount, userId)
                            card.setOnClickListener { showCollectorDetails(userId) }
                            collectorsList.addView(card)
                        }
                }
            }
    }

    private fun createCollectorCardNew(name: String, gid: String, gender: String, location: String, colorHex: String, logCount: Int, userId: String): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                110.dpToPx()
            ).apply { setMargins(0, 0, 0, 16.dpToPx()) }
            radius = 28.dpToPx().toFloat()
            setCardBackgroundColor(Color.parseColor(colorHex))
            cardElevation = 0f
        }

        val mainLayout = RelativeLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
        }

        // Circular Profile Image
        val profileCard = MaterialCardView(this).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(80.dpToPx(), 80.dpToPx()).apply {
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            radius = 40.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.WHITE)
            
            val img = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(if (gender == "Female") R.drawable.female_placeholder else R.drawable.male_placeholder)
            }
            addView(img)
        }

        // Info Layout
        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.END_OF, profileCard.id)
                addRule(RelativeLayout.CENTER_VERTICAL)
                marginStart = 16.dpToPx()
            }
            layoutParams = params
        }

        infoLayout.addView(TextView(this).apply {
            text = name
            textSize = 18f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
        })

        infoLayout.addView(TextView(this).apply {
            text = gender
            textSize = 13f
            setTextColor(Color.parseColor("#99000000"))
        })

        infoLayout.addView(TextView(this).apply {
            text = "$logCount collections made"
            textSize = 12f
            setTextColor(Color.parseColor("#99000000"))
            setPadding(0, 2.dpToPx(), 0, 0)
        })

        infoLayout.addView(TextView(this).apply {
            text = "📍 $location"
            textSize = 12f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 6.dpToPx(), 0, 0)
        })

        // Delete Button
        val deleteBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.parseColor("#D32F2F"))
            val params = RelativeLayout.LayoutParams(40.dpToPx(), 40.dpToPx()).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                bottomMargin = 8.dpToPx()
            }
            layoutParams = params
            setOnClickListener {
                confirmDeleteCollector(userId, name)
            }
        }

        mainLayout.addView(profileCard)
        mainLayout.addView(infoLayout)
        mainLayout.addView(deleteBtn)
        
        card.addView(mainLayout)
        return card
    }

    private fun confirmDeleteCollector(userId: String, name: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Collector")
            .setMessage("Are you sure you want to delete $name? They will no longer be able to log in.")
            .setPositiveButton("Delete") { _, _ ->
                deleteCollector(userId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCollector(userId: String) {
        db.collection("Users").document(userId).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Collector deleted successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting collector: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showCollectorDetails(userId: String) {
        db.collection("Users").document(userId).get().addOnSuccessListener { doc ->
            if (doc != null) {
                val email = doc.getString("email")
                val gid = doc.getString("gjengeID")
                val nID = doc.getString("nationalID") ?: "Not provided"
                val location = doc.getString("location") ?: "Not provided"
                val gender = doc.getString("gender") ?: "Not specified"
                val earnings = doc.getDouble("totalEarnings") ?: 0.0
                val credits = doc.getLong("totalCredits") ?: 0L
                val isVerified = doc.getBoolean("idVerified") ?: false

                val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_collector_info, null)
                val infoTxt = dialogView.findViewById<TextView>(R.id.collectorInfoText)
                val verifyBtn = dialogView.findViewById<Button>(R.id.btnVerifyId)
                
                infoTxt.text = """
                    Email: $email
                    Gjenge ID: $gid
                    National ID: $nID
                    Location: $location
                    Gender: $gender
                    Current Balance: KSh $earnings
                    Total Credits: $credits pts
                    Verification: ${if (isVerified) "Verified" else "Not Verified"}
                """.trimIndent()

                if (isVerified) verifyBtn.visibility = View.GONE

                val dialog = AlertDialog.Builder(this)
                    .setTitle("Collector Details")
                    .setView(dialogView)
                    .setPositiveButton("Close", null)
                    .show()

                verifyBtn.setOnClickListener {
                    db.collection("Users").document(userId).update("idVerified", true)
                        .addOnSuccessListener {
                            Toast.makeText(this, "ID Verified", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                }
            }
        }
    }

    private fun updateLogStatus(userId: String?, logId: String, newStatus: String) {
        if (userId == null) return
        db.collection("Users").document(userId).collection("Logs").document(logId)
            .update("status", newStatus)
            .addOnSuccessListener {
                Toast.makeText(this, "Log $newStatus", Toast.LENGTH_SHORT).show()
                if (newStatus == "Approved") {
                    db.collection("Users").document(userId).collection("Logs").document(logId).get()
                        .addOnSuccessListener { logDoc ->
                            val payment = logDoc.getDouble("payment") ?: 0.0
                            val credits = logDoc.getLong("credits")?.toInt() ?: 0
                            db.collection("Users").document(userId).get().addOnSuccessListener { userDoc ->
                                val oldEarnings = userDoc.getDouble("totalEarnings") ?: 0.0
                                val oldCredits = userDoc.getLong("totalCredits")?.toInt() ?: 0
                                db.collection("Users").document(userId).update(mapOf(
                                    "totalEarnings" to (oldEarnings + payment),
                                    "totalCredits" to (oldCredits + credits)
                                ))
                            }
                        }
                }
            }
    }

    private fun approveWithdrawal(docId: String, userId: String, amount: Double) {
        db.collection("Withdrawals").document(docId).update("status", "Paid")
            .addOnSuccessListener {
                db.collection("Users").document(userId).get().addOnSuccessListener { userDoc ->
                    val currentEarnings = userDoc.getDouble("totalEarnings") ?: 0.0
                    val currentCredits = userDoc.getLong("totalCredits")?.toInt() ?: 0
                    val email = userDoc.getString("email") ?: ""
                    val updates = mapOf(
                        "totalEarnings" to (currentEarnings - amount),
                        "totalCredits" to (currentCredits - amount.toInt()).let { if (it < 0) 0 else it }
                    )
                    db.collection("Users").document(userId).update(updates).addOnSuccessListener {
                        sendApprovalEmail(email, amount)
                    }
                }
            }
    }

    private fun sendApprovalEmail(email: String, amount: Double) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, "Gjenge Plastic Ledger: Withdrawal Approved")
            putExtra(Intent.EXTRA_TEXT, "Hello,\n\nYour withdrawal request for KSh $amount has been approved.\n\nThank you!")
        }
        try { startActivity(Intent.createChooser(intent, "Send Email")) } catch (e: Exception) {}
    }

    private fun createAdminCard(titleStr: String, status: String, time: Long, collectorName: String, gjengeId: String): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16.dpToPx()) }
            radius = 24.dpToPx().toFloat()
            setCardBackgroundColor(Color.WHITE)
            cardElevation = 2.dpToPx().toFloat()
            strokeWidth = 1.dpToPx()
            strokeColor = Color.parseColor("#F3F4F6")
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 24.dpToPx())
        }

        // Header with Avatar and Name
        val headerLayout = RelativeLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val avatar = MaterialCardView(this).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(40.dpToPx(), 40.dpToPx())
            radius = 20.dpToPx().toFloat()
            setCardBackgroundColor(Color.parseColor("#F3F4F6"))
            cardElevation = 0f
            
            addView(TextView(context).apply {
                text = collectorName.take(1).uppercase()
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#6B7280"))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            })
        }

        val nameInfoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val params = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
            params.addRule(RelativeLayout.END_OF, avatar.id)
            params.marginStart = 12.dpToPx()
            layoutParams = params
        }

        nameInfoLayout.addView(TextView(this).apply {
            text = collectorName
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
        })

        nameInfoLayout.addView(TextView(this).apply {
            text = "ID: $gjengeId"
            textSize = 12f
            setTextColor(Color.parseColor("#6B7280"))
        })

        val statusBadge = TextView(this).apply {
            text = status
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            val badgeColor = when (status) {
                "Pending" -> Color.parseColor("#FEF3C7") to Color.parseColor("#92400E")
                "Approved", "Paid" -> Color.parseColor("#D1FAE5") to Color.parseColor("#065F46")
                "Denied", "Rejected" -> Color.parseColor("#FEE2E2") to Color.parseColor("#991B1B")
                else -> Color.parseColor("#F3F4F6") to Color.parseColor("#374151")
            }
            setBackgroundResource(android.R.drawable.editbox_dropdown_light_frame) // Temporary background
            setPadding(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 4.dpToPx())
            setTextColor(badgeColor.second)
            
            val params = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
            params.addRule(RelativeLayout.ALIGN_PARENT_END)
            params.addRule(RelativeLayout.CENTER_VERTICAL)
            layoutParams = params
        }

        headerLayout.addView(avatar)
        headerLayout.addView(nameInfoLayout)
        headerLayout.addView(statusBadge)

        mainLayout.addView(headerLayout)

        // Title and Time
        mainLayout.addView(TextView(this).apply {
            text = titleStr
            textSize = 18f
            setTextColor(Color.parseColor("#1F2937"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 16.dpToPx(), 0, 4.dpToPx())
        })

        mainLayout.addView(TextView(this).apply {
            text = sdf.format(Date(time))
            textSize = 12f
            setTextColor(Color.parseColor("#9CA3AF"))
        })

        card.addView(mainLayout)
        return card
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}

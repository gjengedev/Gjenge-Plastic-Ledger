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
    private lateinit var collectorsGrid: GridLayout
    private lateinit var sectionTitle: TextView
    private val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    private val cardColors = listOf("#FEE2E2", "#E0F2FE", "#DCFCE7", "#FEF9C3", "#F3E8FF", "#FFEDD5")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        itemsList = findViewById(R.id.adminItemsList)
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
        collectorsGrid.visibility = if (type == "Collectors") View.VISIBLE else View.GONE
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

                    val card = createAdminCard("$type Collection - $weight Kg", status, timestamp)
                    
                    if (status == "Pending") {
                        val btnLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                        val approveBtn = Button(this).apply {
                            text = "Approve"
                            setBackgroundColor(Color.parseColor("#16A34A"))
                            setOnClickListener { updateLogStatus(userRef?.id, logId, "Approved") }
                        }
                        val denyBtn = Button(this).apply {
                            text = "Deny"
                            setBackgroundColor(Color.parseColor("#D32F2F"))
                            setOnClickListener { updateLogStatus(userRef?.id, logId, "Denied") }
                        }
                        btnLayout.addView(approveBtn)
                        btnLayout.addView(denyBtn)
                        (card.getChildAt(0) as LinearLayout).addView(btnLayout)
                    }
                    itemsList.addView(card)
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

                    val card = createAdminCard("Withdrawal: KSh $amount ($name)", status, timestamp)

                    if (status == "Pending") {
                        val approveBtn = Button(this).apply {
                            text = "Process & Email"
                            setBackgroundColor(Color.parseColor("#16A34A"))
                            setOnClickListener { approveWithdrawal(docId, userId, amount) }
                        }
                        (card.getChildAt(0) as LinearLayout).addView(approveBtn)
                    }
                    itemsList.addView(card)
                }
            }
    }

    private fun listenForCollectors() {
        db.collection("Users").whereEqualTo("role", "Collector")
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                collectorsGrid.removeAllViews()

                snapshots?.forEachIndexed { index, doc ->
                    val name = doc.getString("email")?.split("@")?.get(0) ?: "User"
                    val gjengeId = doc.getString("gjengeID") ?: "GPL-NEW"
                    val color = cardColors[index % cardColors.size]
                    val userId = doc.id

                    db.collection("Users").document(userId).collection("Logs").get()
                        .addOnSuccessListener { logs ->
                            val logCount = logs.size()
                            val card = createCollectorCard(name, gjengeId, color, logCount)
                            card.setOnClickListener { showCollectorDetails(userId) }
                            collectorsGrid.addView(card)
                        }
                }
            }
    }

    private fun createCollectorCard(name: String, gid: String, colorHex: String, logCount: Int): MaterialCardView {
        val card = MaterialCardView(this).apply {
            val params = GridLayout.LayoutParams()
            params.width = 0
            params.height = GridLayout.LayoutParams.WRAP_CONTENT
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            params.setMargins(8, 8, 8, 8)
            layoutParams = params
            radius = 32f
            setCardBackgroundColor(Color.parseColor(colorHex))
            cardElevation = 0f
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 48, 32, 48)
        }

        layout.addView(TextView(this).apply {
            text = name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            textSize = 18f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        layout.addView(TextView(this).apply {
            text = gid
            textSize = 12f
            setTextColor(Color.parseColor("#66000000"))
            setPadding(0, 8, 0, 0)
        })

        layout.addView(TextView(this).apply {
            text = "$logCount logs made"
            textSize = 12f
            setTextColor(Color.parseColor("#99000000"))
            setPadding(0, 4, 0, 0)
        })

        card.addView(layout)
        return card
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

    private fun createAdminCard(titleStr: String, status: String, time: Long): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
            radius = 24f
            setCardBackgroundColor(Color.WHITE)
            cardElevation = 2f
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        layout.addView(TextView(this).apply {
            text = titleStr
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
        })
        layout.addView(TextView(this).apply {
            text = "Status: $status | ${sdf.format(Date(time))}"
            textSize = 13f
            setTextColor(when (status) {
                "Pending" -> Color.parseColor("#CA8A04")
                "Approved" -> Color.parseColor("#16A34A")
                "Denied" -> Color.parseColor("#D32F2F")
                else -> Color.parseColor("#16A34A")
            })
        })
        card.addView(layout)
        return card
    }
}

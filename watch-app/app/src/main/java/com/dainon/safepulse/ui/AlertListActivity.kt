package com.dainon.safepulse.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.*
import com.dainon.safepulse.R
import com.dainon.safepulse.data.AlertMessage
import com.dainon.safepulse.data.ServerClient
import kotlinx.coroutines.*

/**
 * 관제센터 알림 내역 화면
 */
class AlertListActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private val adapter = AlertAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert_list)

        recyclerView = findViewById(R.id.rvAlerts)
        tvEmpty = findViewById(R.id.tvEmpty)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadAlerts()
    }

    private fun loadAlerts() {
        scope.launch {
            val alerts = ServerClient.getAlerts(30)
            if (alerts.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                adapter.setData(alerts)
            }
        }

        // 10초마다 갱신
        scope.launch {
            while (isActive) {
                delay(10000)
                val alerts = ServerClient.getAlerts(30)
                if (alerts.isNotEmpty()) adapter.setData(alerts)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

/** 알림 목록 어댑터 */
class AlertAdapter : RecyclerView.Adapter<AlertAdapter.VH>() {

    private val items = mutableListOf<AlertMessage>()

    fun setData(data: List<AlertMessage>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alert, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvLevel: TextView = view.findViewById(R.id.tvLevel)
        private val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        private val tvTime: TextView = view.findViewById(R.id.tvTime)
        private val indicator: View = view.findViewById(R.id.indicator)

        fun bind(alert: AlertMessage) {
            tvLevel.text = when (alert.level) {
                "danger" -> "위험"
                "warning" -> "경고"
                "caution" -> "주의"
                else -> "정보"
            }
            tvMessage.text = alert.message
            tvTime.text = alert.timestamp.takeLast(8) // HH:MM:SS

            val color = when (alert.level) {
                "danger" -> 0xFFE53935.toInt()
                "warning" -> 0xFFFF9800.toInt()
                "caution" -> 0xFF42A5F5.toInt()
                else -> 0xFF66BB6A.toInt()
            }
            tvLevel.setTextColor(color)
            indicator.setBackgroundColor(color)
        }
    }
}
